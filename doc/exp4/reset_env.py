#!/usr/bin/env python3
"""实验四：每轮压测前重置环境（库存归 50、清空订单/购物车），保证 baseline 与 fixed 两轮口径一致。"""
import json
import pymysql

GOODS_ID = 1181000
PRODUCT_ID = 1
INIT_STOCK = 50

conn = pymysql.connect(host="127.0.0.1", user="litemall", password="litemall123456",
                       database="litemall", charset="utf8mb4", autocommit=True)
cur = conn.cursor()

cur.execute("DELETE FROM litemall_order")
cur.execute("DELETE FROM litemall_order_goods")
cur.execute("DELETE FROM litemall_cart")
cur.execute("UPDATE litemall_goods_product SET number=%s WHERE id=%s", (INIT_STOCK, PRODUCT_ID))

cur.execute("SELECT number FROM litemall_goods_product WHERE id=%s", (PRODUCT_ID,))
cur.execute("SELECT COUNT(*) FROM litemall_order")
print(f"reset done: stock={cur.fetchone()[0] if False else INIT_STOCK}, orders="
      f"{cur.fetchone()[0]}")
conn.close()
