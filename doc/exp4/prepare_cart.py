#!/usr/bin/env python3
"""实验四：重置压测环境 + 给全部压测账号预置购物车（各 1 件），
使 200 个虚拟用户能同时冲击下单接口，而不是在加购环节就被拦截。"""
import json
import pymysql

GOODS_ID = 1181000
PRODUCT_ID = 1
INIT_STOCK = int(__import__("os").getenv("INIT_STOCK", "50"))

conn = pymysql.connect(host="127.0.0.1", user="litemall", password="litemall123456",
                       database="litemall", charset="utf8mb4", autocommit=True)
cur = conn.cursor()

cur.execute("DELETE FROM litemall_order")
cur.execute("DELETE FROM litemall_order_goods")
cur.execute("DELETE FROM litemall_cart")
cur.execute("UPDATE litemall_goods_product SET number=%s WHERE id=%s", (INIT_STOCK, PRODUCT_ID))

users = json.load(open("/home/user/Doubao/chats/38450576674735618/exp4/stress_users.json", encoding="utf-8"))
for u in users:
    cur.execute(
        """INSERT INTO litemall_cart
           (user_id, goods_id, goods_sn, goods_name, product_id, price, number,
            specifications, checked, pic_url, add_time, update_time, deleted)
           VALUES (%s,%s,'1181000','母亲节礼物-舒适安睡组合',%s,999.00,1,'[]',1,'',NOW(),NOW(),0)""",
        (u["user_id"], GOODS_ID, PRODUCT_ID))

cur.execute("SELECT COUNT(*) FROM litemall_cart")
carts = cur.fetchone()[0]
cur.execute("SELECT number FROM litemall_goods_product WHERE id=%s", (PRODUCT_ID,))
stock = cur.fetchone()[0]
print(f"prepare done: carts={carts} stock={stock}")
conn.close()
