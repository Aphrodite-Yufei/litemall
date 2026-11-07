# 实验二 手工 CFG 与基路径分析

被测方法：`org.linlinjava.litemall.db.service.CouponVerifyService#checkCoupon`
签名：`LitemallCoupon checkCoupon(Integer userId, Integer couponId, Integer userCouponId, BigDecimal checkedGoodsPrice, List<LitemallCart> cartList)`

## 一、判定节点（D）清单

| 编号 | 判定 | 位置 | 判定结果 |
|---|---|---|---|
| D1 | `coupon == null` | 方法入口 | T→返回 null |
| D2 | `coupon.getDeleted()` | 方法入口 | T→返回 null |
| D3 | `couponUser == null`（带 userCouponId 查） | 入口后 | T→走 queryOne 兜底；F→校验 couponId |
| D4 | `!couponId.equals(couponUser.getCouponId())` | D3 的 F 分支 | T→返回 null |
| D5 | `couponUser == null`（兜底后） | 兜底后 | T→返回 null |
| D6 | `timeType.equals(TIME_TYPE_TIME)` | 超期检查 | T→进时间窗检查；F→进 D8 |
| D7 | `now.isBefore(startTime) \|\| now.isAfter(endTime)` | D6 的 T 分支 | T→返回 null |
| D8 | `timeType.equals(TIME_TYPE_DAYS)` | D6 的 F 分支 | T→进天数检查；F→D10 |
| D9 | `now.isAfter(addTime.plusDays(days))` | D8 的 T 分支 | T→返回 null |
| D10 | `else`（其他 timeType） | 兜底 | 恒 T→返回 null |
| D11 | `goodType.equals(CATEGORY) \|\| goodType.equals(ARRAY)` | 商品匹配前置 | T→构建 cartMap；F→跳过 |
| D12 | `for (LitemallCart cart : cartList)` | D11 的 T 分支内 | 循环 |
| D13 | `carts == null`（三元 key 计算后） | 循环体 | T→new LinkedList |
| D14 | `for (Integer goodsId : goodsValueList)` | retainAll 后 | 循环 |
| D15 | `for (LitemallCart cart : carts)` | 内层循环 | 循环 |
| D16 | `goodsValueList.isEmpty() \|\| total.compareTo(min) == -1` | 累加后 | T→返回 null |
| D17 | `!status.equals(STATUS_NORMAL)` | 订单状态检查 | T→返回 null |
| D18 | `checkedGoodsPrice.compareTo(min) == -1` | 最低消费检查 | T→返回 null |
| 终点 | `return coupon` | 方法出口 | — |

### 圈复杂度计算

- 结构化判定（if / else-if / 三元 / `||` 条件各计 1）：D1–D18 共 18 个判定 + 1 个方法体
- **V(G) = 18 + 1 = 19**（含 2 个循环、3 个复合 `||` 条件）
- 与 JaCoCo "分支覆盖率" 口径一致：18 个判定共 36 个分支走向

## 二、基路径（独立路径）集合

| 编号 | 路径描述 | 期望输出 |
|---|---|---|
| P1 | D1=T | null |
| P2 | D1=F → D2=T | null |
| P3 | D2=F → D3=T → D5=T | null |
| P4 | D2=F → D3=F → D4=T | null |
| P5 | D4=F → D5=F → D6=T → D7=T（未开始/已结束） | null |
| P6 | D4=F → D5=F → D6=T → D7=F → D11=F → D17=T（状态非正常） | null |
| P7 | D6=T → D7=F → D11=F → D17=F → D18=T（价格未达门槛） | null |
| P8 | D6=T → D7=F → D11=F → D17=F → D18=F → return | **coupon** |
| P9 | D6=F → D8=T → D9=T（天数过期） | null |
| P10 | D8=F → D10=T（非法 timeType） | null |
| P11 | D6=T → D7=F → D11=T → D16=T（分类/商品不匹配） | null |
| P12 | D6=T → D7=F → D11=T → D12 循环 → D13=T → D16=F → D17=F → D18=F | **coupon** |
| P13 | D6=T → D7=F → D11=T → D12 循环 → D13=F → D15 循环 → D16=T | null |

共 13 条基路径（P1–P13），覆盖全部 18 个判定节点的 T/F 两侧。

## 三、潜在缺陷（被测逻辑本身）

1. **`goodsService.findById(cart.getGoodsId())` 可能返回 null** → 紧接着 `.getCategoryId()` 触发 **NPE**（D11 分支为 CATEGORY 时）。这是业务代码的真实缺陷，测试中应显式暴露（用 assertThrows 或发现异常）。
2. `coupon.getGoodsValue()` 为 null 时 `Arrays.asList(null)` 抛 NPE（优惠券未配置商品范围）。
3. 时间窗口使用 `LocalDateTime.now()`，测试需要构造"未来/过去"的 startTime/endTime 来避免用例随时间失效。
