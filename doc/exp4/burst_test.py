#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实验四 · 并发突发测试（复现/验证超卖）
200 个已登录、购物车已预置 1 件商品的账号，在同一瞬间并发提交订单。
所有线程用 threading.Barrier 同步起跑，尽量收窄发起时间差，放大竞态窗口。

用法：
    python3 burst_test.py baseline     # 改造前
    python3 burst_test.py fixed        # 改造后
"""
import json
import sys
import threading
import time
from collections import Counter

import pymysql
import requests

BASE = "http://127.0.0.1:8082"
GOODS_ID = 1181000
PRODUCT_ID = 1
INIT_STOCK = int(__import__("os").getenv("INIT_STOCK", "50"))
USERS_FILE = "/home/user/Doubao/chats/38450576674735618/exp4/stress_users.json"

tag = sys.argv[1] if len(sys.argv) > 1 else "run"
users = json.load(open(USERS_FILE, encoding="utf-8"))
N = len(users)

# ---------- 1. 预热登录 ----------
tokens, addr_ids = [], []
for u in users:
    r = requests.post(f"{BASE}/wx/auth/login",
                      json={"username": u["username"], "password": u["password"]}, timeout=15)
    tokens.append(r.json()["data"]["token"])
    addr_ids.append(u["address_id"])
print(f"[pre] 登录完成 {len(tokens)}/{N}")

# ---------- 2. 同时起跑 ----------
barrier = threading.Barrier(N)
results = []
lock = threading.Lock()


def one(i):
    token, addr = tokens[i], addr_ids[i]
    headers = {"X-Litemall-Token": token, "Content-Type": "application/json"}
    body = {"cartId": 0, "addressId": addr, "couponId": 0, "userCouponId": 0,
            "grouponRulesId": 0, "message": ""}
    barrier.wait()
    t0 = time.perf_counter()
    try:
        r = requests.post(f"{BASE}/wx/order/submit", json=body, headers=headers, timeout=30)
        dt = (time.perf_counter() - t0) * 1000
        payload = r.json() if r.headers.get("Content-Type", "").startswith("application/json") else {}
        rec = {"http": r.status_code, "errno": payload.get("errno"),
               "orderId": (payload.get("data") or {}).get("orderId"), "ms": round(dt, 1)}
    except Exception as e:  # noqa: BLE001
        rec = {"http": -1, "errno": None, "orderId": None, "ms": -1, "error": str(e)[:80]}
    with lock:
        results.append(rec)


threads = [threading.Thread(target=one, args=(i,)) for i in range(N)]
t_start = time.perf_counter()
for t in threads:
    t.start()
for t in threads:
    t.join()
wall = (time.perf_counter() - t_start) * 1000

# ---------- 3. 汇总 ----------
ok = [r for r in results if r["errno"] == 0]
errnos = Counter(str(r["errno"]) for r in results)
lat = sorted(r["ms"] for r in results if r["ms"] > 0)
p95 = lat[int(len(lat) * 0.95) - 1] if lat else None
p99 = lat[int(len(lat) * 0.99) - 1] if lat else None

conn = pymysql.connect(host="127.0.0.1", user="litemall", password="litemall123456",
                       database="litemall", charset="utf8mb4")
cur = conn.cursor()
cur.execute("SELECT COUNT(*) FROM litemall_order")
db_orders = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM litemall_order_goods")
db_order_goods = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM litemall_order_goods WHERE product_id=%s", (PRODUCT_ID,))
db_sold_qty = cur.fetchone()[0]
cur.execute("SELECT number FROM litemall_goods_product WHERE id=%s", (PRODUCT_ID,))
final_stock = cur.fetchone()[0]
conn.close()

report = {
    "tag": tag,
    "concurrency": N,
    "init_stock": INIT_STOCK,
    "wall_ms": round(wall, 1),
    "api_success": len(ok),
    "api_errno_dist": dict(errnos),
    "db_orders": db_orders,
    "db_sold_qty": db_sold_qty,
    "final_stock": final_stock,
    "p95_ms": p95,
    "p99_ms": p99,
    "oversold_api": len(ok) > INIT_STOCK,
    "oversold_db": db_sold_qty > INIT_STOCK,
    "ghost_orders": db_orders - len(ok),
}
print(json.dumps(report, ensure_ascii=False, indent=2))
with open(f"/home/user/Doubao/chats/38450576674735618/exp4/burst_{tag}.json", "w", encoding="utf-8") as f:
    json.dump({"report": report, "raw": results}, f, ensure_ascii=False, indent=2)
