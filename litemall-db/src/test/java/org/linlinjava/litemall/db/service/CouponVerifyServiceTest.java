package org.linlinjava.litemall.db.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.util.CouponConstant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * CouponVerifyService#checkCoupon 白盒单元测试。
 * 由 DeepSeek 生成初版用例，人工补齐基路径 P1-P13（见实验二报告 CFG 分析）。
 */
@ExtendWith(MockitoExtension.class)
class CouponVerifyServiceTest {

    @Mock
    private LitemallCouponUserService couponUserService;
    @Mock
    private LitemallCouponService couponService;
    @Mock
    private LitemallGoodsService goodsService;
    @InjectMocks
    private CouponVerifyService service;

    private static final Integer USER_ID = 100;
    private static final Integer COUPON_ID = 1;
    private static final Integer USER_COUPON_ID = 10;

    // ---------- helpers ----------

    private LitemallCoupon newCoupon() {
        LitemallCoupon c = new LitemallCoupon();
        c.setId(COUPON_ID);
        c.setName("满50减10");
        c.setDeleted(false);
        c.setStatus(CouponConstant.STATUS_NORMAL);
        c.setTimeType(CouponConstant.TIME_TYPE_TIME);
        c.setStartTime(LocalDateTime.now().minusDays(1));
        c.setEndTime(LocalDateTime.now().plusDays(1));
        c.setGoodsType(CouponConstant.GOODS_TYPE_ALL);
        c.setGoodsValue(new Integer[]{1, 2});
        c.setMin(new BigDecimal("50"));
        return c;
    }

    private LitemallCouponUser newCouponUser() {
        LitemallCouponUser cu = new LitemallCouponUser();
        cu.setId(USER_COUPON_ID);
        cu.setUserId(USER_ID);
        cu.setCouponId(COUPON_ID);
        cu.setAddTime(LocalDateTime.now().minusDays(1));
        return cu;
    }

    private LitemallCart newCart(Integer goodsId, String price, int number) {
        LitemallCart cart = new LitemallCart();
        cart.setGoodsId(goodsId);
        cart.setPrice(new BigDecimal(price));
        cart.setNumber((short) number);
        return cart;
    }

    // ---------- P1/P2 优惠券不存在或已删除 ----------

    @Test
    @org.junit.jupiter.api.DisplayName("P1 coupon 为 null 时返回 null")
    void checkCoupon_couponNotFound_returnsNull() {
        when(couponService.findById(COUPON_ID)).thenReturn(null);
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P2 优惠券已逻辑删除时返回 null")
    void checkCoupon_couponDeleted_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setDeleted(true);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    // ---------- P3/P4/P5 用户券校验 ----------

    @Test
    @org.junit.jupiter.api.DisplayName("P3 用户券与兜底查询均为空时返回 null")
    void checkCoupon_couponUserNotFound_returnsNull() {
        when(couponService.findById(COUPON_ID)).thenReturn(newCoupon());
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(null);
        when(couponUserService.queryOne(USER_ID, COUPON_ID)).thenReturn(null);
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
        verify(couponUserService).queryOne(USER_ID, COUPON_ID);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P4 用户券 couponId 与入参不符时返回 null")
    void checkCoupon_couponUserCouponIdMismatch_returnsNull() {
        LitemallCouponUser cu = newCouponUser();
        cu.setCouponId(999);
        when(couponService.findById(COUPON_ID)).thenReturn(newCoupon());
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(cu);
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
        verify(couponUserService, never()).queryOne(anyInt(), anyInt());
    }

    // ---------- P5/P7/P8 时间窗 ----------

    @Test
    @org.junit.jupiter.api.DisplayName("P5 时间型优惠券已过期时返回 null")
    void checkCoupon_couponTimeExpired_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setEndTime(LocalDateTime.now().minusMinutes(5));
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P6 时间型优惠券尚未开始时返回 null")
    void checkCoupon_couponNotStarted_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setStartTime(LocalDateTime.now().plusHours(1));
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P7 天数型优惠券未过期且满足门槛时返回 coupon")
    void checkCoupon_daysTypeValid_returnsCoupon() {
        LitemallCoupon coupon = newCoupon();
        coupon.setTimeType(CouponConstant.TIME_TYPE_DAYS);
        coupon.setDays((short) 7);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNotNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("60"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P9 天数型优惠券已过期时返回 null")
    void checkCoupon_daysTypeExpired_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setTimeType(CouponConstant.TIME_TYPE_DAYS);
        coupon.setDays((short) 1);
        LitemallCouponUser cu = newCouponUser();
        cu.setAddTime(LocalDateTime.now().minusDays(3));
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(cu);
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P10 非法 timeType 时返回 null")
    void checkCoupon_unknownTimeType_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setTimeType((short) 9);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    // ---------- P11/P12 商品范围匹配 ----------

    @Test
    @org.junit.jupiter.api.DisplayName("P11 指定商品类型但购物车无匹配商品时返回 null")
    void checkCoupon_goodsTypeArrayNotMatched_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_ARRAY);
        coupon.setGoodsValue(new Integer[]{1});
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        List<LitemallCart> carts = Arrays.asList(newCart(2, "100", 1));
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), carts));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P12 指定商品匹配且满减时返回 coupon")
    void checkCoupon_goodsTypeArrayMatched_returnsCoupon() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_ARRAY);
        coupon.setGoodsValue(new Integer[]{1});
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        List<LitemallCart> carts = Arrays.asList(newCart(1, "100", 1));
        assertNotNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), carts));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P12c 同一商品多行时走 cartMap 复用分支")
    void checkCoupon_duplicateCartSameGoods_returnsCoupon() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_ARRAY);
        coupon.setGoodsValue(new Integer[]{1});
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        List<LitemallCart> carts = Arrays.asList(newCart(1, "30", 1), newCart(1, "30", 1));
        assertNotNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("60"), carts));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P11b 商品匹配但总价未达门槛时返回 null")
    void checkCoupon_goodsMatchedButTotalBelowMin_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_ARRAY);
        coupon.setGoodsValue(new Integer[]{1});
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        List<LitemallCart> carts = Arrays.asList(newCart(1, "10", 1));
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), carts));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P11c 空购物车时 goodsValueList 为空返回 null")
    void checkCoupon_emptyCartList_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_ARRAY);
        coupon.setGoodsValue(new Integer[]{1});
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), new ArrayList<>()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P2b 优惠券未删除时不会被误判")
    void checkCoupon_couponNotNullAndNotDeleted_notShortCircuited() {
        LitemallCoupon coupon = newCoupon();
        coupon.setDeleted(false);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertEquals(coupon, service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("60"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("P12b 分类类型通过 goodsService 查出分类并匹配")
    void checkCoupon_goodsTypeCategoryMatched_returnsCoupon() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_CATEGORY);
        coupon.setGoodsValue(new Integer[]{8});
        LitemallGoods goods = new LitemallGoods();
        goods.setId(1);
        goods.setCategoryId(8);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        when(goodsService.findById(1)).thenReturn(goods);
        assertNotNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Arrays.asList(newCart(1, "100", 1))));
        verify(goodsService).findById(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("已知缺陷：分类类型下 goods 为 null 触发 NPE")
    void checkCoupon_goodsTypeCategoryGoodsNull_throwsNpe() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_CATEGORY);
        coupon.setGoodsValue(new Integer[]{8});
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        when(goodsService.findById(1)).thenReturn(null);
        assertThrows(NullPointerException.class, () ->
                service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                        new BigDecimal("100"), Arrays.asList(newCart(1, "100", 1))));
    }

    // ---------- P13/P6 状态与最低消费 ----------

    @Test
    @org.junit.jupiter.api.DisplayName("状态非正常时返回 null")
    void checkCoupon_statusNotNormal_returnsNull() {
        LitemallCoupon coupon = newCoupon();
        coupon.setStatus(CouponConstant.STATUS_EXPIRED);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("订单金额低于门槛时返回 null")
    void checkCoupon_priceBelowMin_returnsNull() {
        when(couponService.findById(COUPON_ID)).thenReturn(newCoupon());
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("49"), Collections.emptyList()));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("全部条件满足时返回优惠券本身")
    void checkCoupon_allConditionsMet_returnsSameCoupon() {
        LitemallCoupon coupon = newCoupon();
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        LitemallCoupon result = service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("60"), Collections.emptyList());
        assertEquals(coupon, result);
        assertEquals(Integer.valueOf(COUPON_ID), result.getId());
        verify(couponUserService, never()).queryOne(anyInt(), anyInt());
    }
}
