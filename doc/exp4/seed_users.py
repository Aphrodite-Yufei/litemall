#!/usr/bin/env python3
"""为实验四压测准备数据：200 个独立测试账号 + 每人一条收货地址 + 目标货品库存置 50。"""
import json
import bcrypt
import pymysql

N = 200
PASSWORD = "123456"
GOODS_ID = 1181000
PRODUCT_ID = 1
INIT_STOCK = 50

conn = pymysql.connect(host="127.0.0.1", user="litemall", password="litemall123456",
                       database="litemall", charset="utf8mb4", autocommit=True)
cur = conn.cursor()

pw_hash = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt()).decode()

cur.execute("DELETE FROM litemall_user WHERE username LIKE 'stress%%'")
cur.execute("DELETE FROM litemall_address WHERE tel = '13800000000'")

users = []
for i in range(1, N + 1):
    uname = f"stress{i:03d}"
    cur.execute(
        """INSERT INTO litemall_user
           (username, password, gender, last_login_ip, nickname, mobile, avatar,
            weixin_openid, session_key, status, add_time, update_time, deleted)
           VALUES (%s,%s,0,'127.0.0.1',%s,'13800000000','',
                   %s,'', 0, NOW(), NOW(), 0)""",
        (uname, pw_hash, uname, f"stress_openid_{i}"))
    uid = cur.lastrowid
    cur.execute(
        """INSERT INTO litemall_address
           (name, user_id, province, city, county, address_detail, area_code, postal_code,
            tel, is_default, add_time, update_time, deleted)
           VALUES (%s,%s,'北京市','市辖区','东城区',%s,'110101','100000','13800000000',1,NOW(),NOW(),0)""",
        (uname, uid, f"压测地址{i}号"))
    addr_id = cur.lastrowid
    users.append({"user_id": uid, "username": uname, "address_id": addr_id,
                  "password": PASSWORD})

cur.execute("UPDATE litemall_goods_product SET number=%s WHERE id=%s", (INIT_STOCK, PRODUCT_ID))
cur.execute("SELECT id, goods_id, number FROM litemall_goods_product WHERE id=%s", (PRODUCT_ID,))
row = cur.fetchone()

with open("/home/user/Doubao/chats/38450576674735618/exp4/stress_users.json", "w", encoding="utf-8") as f:
    json.dump(users, f, ensure_ascii=False)

print(f"users={len(users)} product={row}")

# 基线快照
cur.execute("SELECT COUNT(*) FROM litemall_order")
print("baseline_orders=", cur.fetchone()[0])
cur.execute("SELECT COUNT(*) FROM litemall_order_goods")
print("baseline_order_goods=", cur.fetchone()[0])
conn.close()
