# 实验四 · 秒杀接口性能压测与并发缺陷复现工具集

被测接口：`POST /wx/order/submit`（litemall-wx-api，Spring Boot + MyBatis + MySQL/MariaDB）

## 1. 环境

| 组件 | 版本/说明 |
|---|---|
| JDK | OpenJDK 11.0.33 |
| MariaDB | 10.6.23（本机 3306，库名 litemall） |
| 应用 | `litemall-wx-api-0.1.0-exec.jar`，端口 8082 |
| 压测器 | Locust 2.x（Python 3.12） |
| 机器 | 2 vCPU / 3 GB 沙箱 |

数据库初始化：依次执行 `litemall-db/sql/` 下的 `litemall_schema.sql`、`litemall_table.sql`、`litemall_data.sql`。

## 2. 脚本

| 文件 | 作用 |
|---|---|
| `seed_users.py` | 创建 200 个独立测试账号（BCrypt 密码 `123456`）+ 每人一条收货地址，并把目标货品库存置为 50 |
| `prepare_cart.py` | 每轮压测前重置：清空订单/购物车、库存归位、给 200 个账号各预置 1 件购物车商品 |
| `burst_test.py` | 并发突发测试：200 线程用 `threading.Barrier` 同一瞬间起跑，抢购同一货品；结束后比对「API 成功数 / 数据库订单数 / 实际售出数量 / 最终库存」，识别幽灵订单与超卖 |
| `locustfile.py` | Locust 持续压测脚本：200 并发、3 分钟，统计 RPS、P50/P95/P99、errno 分布、成功订单号集合 |
| `reset_env.py` | 轻量环境复位（只清订单与库存），用于快速重跑 |

## 3. 关键口径（与 AI 初版脚本的差异）

1. **鉴权**：`/wx/order/submit` 由 `@LoginUser` 拦截，必须带 `X-Litemall-Token`；须先 `POST /wx/auth/login` 换取 JWT。
2. **下单参数**：真实 `SubmitParams` 为 `{cartId, addressId, couponId, userCouponId, grouponRulesId, grouponLinkId, message}`，`cartId=0` 表示结算当前用户全部已勾选购物车。
3. **成功判定**：项目统一响应体是 `{errno, errmsg, data}`，成功为 `errno == 0`，不存在 `code/status/success` 字段。
4. **库存复查**：真实接口是 `GET /wx/goods/detail?id=<goodsId>`，库存位于 `data.productList[].number`；`/wx/product/stock` 为杜撰接口。
5. **超卖判定**：不能只看 API 返回码。必须以数据库复核——`litemall_order` 实际订单条数、`litemall_order_goods` 中该货品的售出总量、`number` 余量三者交叉验证。

## 4. 复现步骤

```bash
# 1) 造账号与库存
python3 seed_users.py

# 2) 重置环境并预置购物车（库存 50）
python3 prepare_cart.py

# 3) 并发突发（200 并发同一瞬间抢 50 件）
python3 burst_test.py baseline

# 4) 持续压测（200 并发 3 分钟）
locust -f locustfile.py --host http://127.0.0.1:8082 --headless \
       --csv baseline --only-summary
```
