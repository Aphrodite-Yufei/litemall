#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实验四 · litemall 秒杀接口并发压测脚本（Locust）
被测：POST /wx/order/submit
场景：200 个独立账号并发抢购同一货品（库存仅 50，每人每次买 1 件），持续 3 分钟

与 AI 初版（DeepSeek 输出）的关键差异，均为人工审计后修正：
1. 补登录：/wx/order/submit 需要 @LoginUser 鉴权（Header: X-Litemall-Token），AI 版直接 POST 会被 501 拦截；
2. 下单参数改为真实 SubmitParams：cartId/addressId/couponId/grouponRulesId/message，AI 版写的 productId/quantity 不存在；
3. 业务成功判定改为项目真实字段 errno（0 为成功），AI 版判 code/status/success 恒为失败；
4. 库存复查改走真实存在的 /wx/goods/detail（data.productList），AI 版 GET /wx/product/stock 是杜撰接口；
5. 补订单号唯一集合统计，落实 AI 版判定标准里"以订单号复核"但脚本从未实现的那一条。

运行：
    locust -f locustfile.py --host http://127.0.0.1:8082 --headless -u 200 -r 50 -t 3m \
           --csv results/baseline
"""
import json
import os
import queue
import threading
import time

from locust import HttpUser, task, between, events, LoadTestShape
from locust.exception import StopUser

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
USER_FILE = os.path.join(BASE_DIR, "stress_users.json")
RESULT_FILE = os.path.join(BASE_DIR, "result.json")

GOODS_ID = int(os.getenv("GOODS_ID", "1181000"))
PRODUCT_ID = int(os.getenv("PRODUCT_ID", "1"))
INIT_STOCK = int(os.getenv("INIT_STOCK", "50"))

_tokens = queue.Queue()
if os.path.exists(USER_FILE):
    with open(USER_FILE, encoding="utf-8") as f:
        for u in json.load(f):
            _tokens.put(u)

_lock = threading.Lock()
success_ids = set()          # 业务成功（errno==0）订单号
errno_counter = {}           # 失败原因分布
request_counter = {"add": 0, "submit": 0}
final_stock = [None]


def _dump_result(env):
    total = env.stats.total
    data = {
        "success_orders": len(success_ids),
        "success_order_ids": sorted(success_ids)[:200],
        "errno_distribution": errno_counter,
        "requests": dict(request_counter),
        "rps": round(total.total_rps, 2),
        "p50": total.get_response_time_percentile(0.50),
        "p95": total.get_response_time_percentile(0.95),
        "p99": total.get_response_time_percentile(0.99),
        "final_stock": final_stock[0],
        "init_stock": INIT_STOCK,
        "oversold": (len(success_ids) > INIT_STOCK) if final_stock[0] is None else
                    (len(success_ids) > INIT_STOCK or final_stock[0] < 0
                     or final_stock[0] != INIT_STOCK - len(success_ids)),
    }
    with open(RESULT_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("\n===== 压测结论 =====")
    for k, v in data.items():
        if k != "success_order_ids":
            print(f"{k}: {v}")


@events.test_start.add_listener
def _on_start(environment, **kwargs):
    print(f"test start: accounts={_tokens.qsize()} goods={GOODS_ID} product={PRODUCT_ID} stock={INIT_STOCK}")


@events.test_stop.add_listener
def _on_stop(environment, **kwargs):
    # 用真实接口复查最终库存：/wx/goods/detail?id=<goodsId> -> data.productList[].number
    import requests
    host = environment.host
    headers = {}
    try:
        acc = json.load(open(USER_FILE, encoding="utf-8"))[0]
        r = requests.post(f"{host}/wx/auth/login",
                          json={"username": acc["username"], "password": acc["password"]}, timeout=10)
        headers = {"X-Litemall-Token": r.json()["data"]["token"]}
        r = requests.get(f"{host}/wx/goods/detail", params={"id": GOODS_ID}, headers=headers, timeout=10)
        for p in r.json()["data"]["productList"]:
            if p["id"] == PRODUCT_ID:
                final_stock[0] = p["number"]
                break
    except Exception as e:  # noqa: BLE001
        print("库存复查失败:", e)
    _dump_result(environment)


class OrderUser(HttpUser):
    wait_time = between(0.2, 0.6)   # 拟真思考时间，避免把客户端自身变成瓶颈
    host = "http://127.0.0.1:8082"

    def on_start(self):
        self.acc = _tokens.get_nowait() if not _tokens.empty() else None
        if self.acc is None:
            raise StopUser()
        self.fail_streak = 0
        self.token = None
        with self.client.post("/wx/auth/login",
                              json={"username": self.acc["username"], "password": self.acc["password"]},
                              name="POST /wx/auth/login", catch_response=True) as resp:
            if resp.status_code == 200 and resp.json().get("errno") == 0:
                self.token = resp.json()["data"]["token"]
                resp.success()
            else:
                resp.failure(f"login_fail={resp.text[:120]}")
                raise StopUser()

    @task
    def grab(self):
        headers = {"X-Litemall-Token": self.token, "Content-Type": "application/json"}
        # 1) 加车 1 件
        with self.client.post("/wx/cart/add", headers=headers,
                              json={"goodsId": GOODS_ID, "productId": PRODUCT_ID, "number": 1},
                              name="POST /wx/cart/add", catch_response=True) as resp:
            with _lock:
                request_counter["add"] += 1
            body = resp.json() if resp.status_code == 200 else {}
            if body.get("errno") != 0:
                # 商品下架/库存不足等，加车失败就没必要下单
                resp.failure(f"add_errno={body.get('errno')}")
                self._count_errno(body.get("errno"))
                return
            resp.success()

        # 2) 下单（cartId=0 表示结算当前用户全部已选购物车项）
        with self.client.post("/wx/order/submit", headers=headers,
                              json={"cartId": 0, "addressId": self.acc["address_id"],
                                    "couponId": 0, "userCouponId": 0, "grouponRulesId": 0,
                                    "message": ""},
                              name="POST /wx/order/submit", catch_response=True) as resp:
            with _lock:
                request_counter["submit"] += 1
            if resp.status_code != 200:
                resp.failure(f"http={resp.status_code}")
                self.fail_streak += 1
                return
            body = resp.json()
            errno = body.get("errno")
            if errno == 0:
                oid = body["data"]["orderId"]
                with _lock:
                    success_ids.add(oid)
                resp.success()
                self.fail_streak = 0
            else:
                resp.failure(f"errno={errno}")
                self._count_errno(errno)
                self.fail_streak += 1
        if self.fail_streak >= 3:      # 连续 3 次失败视为库存售罄，退出该虚拟用户
            raise StopUser()

    def _count_errno(self, errno):
        with _lock:
            errno_counter[str(errno)] = errno_counter.get(str(errno), 0) + 1


class ThreeMinuteShape(LoadTestShape):
    """按实验说明书要求：200 并发（50/s 爬坡），持续 3 分钟。"""
    users = int(os.getenv("SHAPE_USERS", "200"))
    time_limit = int(os.getenv("SHAPE_DURATION", "180"))

    def tick(self):
        if self.get_run_time() < self.time_limit:
            return (self.users, max(10, self.users // 4))
        return None
