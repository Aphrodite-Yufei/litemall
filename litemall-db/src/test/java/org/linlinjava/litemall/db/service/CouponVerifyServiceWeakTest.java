package org.linlinjava.litemall.db.service;

import org.junit.jupiter.api.DisplayName;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * 弱断言对照组：模拟 AI 初版常见风格（只断言"不抛异常/返回非空"，不校验具体值）。
 * 仅用于实验二对照量化，不参与正式测试集。
 */
@ExtendWith(MockitoExtension.class)
class CouponVerifyServiceWeakTest {

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

    private LitemallCoupon newCoupon() {
        LitemallCoupon c = new LitemallCoupon();
        c.setId(COUPON_ID);
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

    @Test
    @DisplayName("弱断言：coupon 不存在只断言不抛异常")
    void couponNotFound_noThrowOnly() {
        when(couponService.findById(COUPON_ID)).thenReturn(null);
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：coupon 已删除只断言不抛异常")
    void couponDeleted_noThrowOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setDeleted(true);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：用户券不存在只断言不抛异常")
    void couponUserNotFound_noThrowOnly() {
        when(couponService.findById(COUPON_ID)).thenReturn(newCoupon());
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(null);
        when(couponUserService.queryOne(USER_ID, COUPON_ID)).thenReturn(null);
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：券号不匹配只断言不抛异常")
    void couponIdMismatch_noThrowOnly() {
        LitemallCouponUser cu = newCouponUser();
        cu.setCouponId(999);
        when(couponService.findById(COUPON_ID)).thenReturn(newCoupon());
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(cu);
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：时间券过期只断言不抛异常")
    void timeExpired_noThrowOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setEndTime(LocalDateTime.now().minusMinutes(5));
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：时间券未开始只断言不抛异常")
    void timeNotStarted_noThrowOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setStartTime(LocalDateTime.now().plusHours(1));
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：天数券未过期只断言返回非空")
    void daysValid_notNullOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setTimeType(CouponConstant.TIME_TYPE_DAYS);
        coupon.setDays((short) 7);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNotNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("60"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：天数券过期只断言不抛异常")
    void daysExpired_noThrowOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setTimeType(CouponConstant.TIME_TYPE_DAYS);
        coupon.setDays((short) 1);
        LitemallCouponUser cu = newCouponUser();
        cu.setAddTime(LocalDateTime.now().minusDays(3));
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(cu);
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：非法 timeType 只断言不抛异常")
    void unknownTimeType_noThrowOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setTimeType((short) 9);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：指定商品不匹配只断言不抛异常")
    void goodsNotMatched_noThrowOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_ARRAY);
        coupon.setGoodsValue(new Integer[]{1});
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        List<LitemallCart> carts = Arrays.asList(newCart(2, "100", 1));
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), carts));
    }

    @Test
    @DisplayName("弱断言：指定商品匹配只断言返回非空")
    void goodsMatched_notNullOnly() {
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
    @DisplayName("弱断言：分类匹配只断言返回非空")
    void categoryMatched_notNullOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setGoodsType(CouponConstant.GOODS_TYPE_CATEGORY);
        coupon.setGoodsValue(new Integer[]{8});
        LitemallGoods goods = new LitemallGoods();
        goods.setCategoryId(8);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        when(goodsService.findById(1)).thenReturn(goods);
        assertNotNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Arrays.asList(newCart(1, "100", 1))));
    }

    @Test
    @DisplayName("弱断言：状态非正常只断言不抛异常")
    void statusNotNormal_noThrowOnly() {
        LitemallCoupon coupon = newCoupon();
        coupon.setStatus(CouponConstant.STATUS_EXPIRED);
        when(couponService.findById(COUPON_ID)).thenReturn(coupon);
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("100"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：金额不足只断言不抛异常")
    void priceBelowMin_noThrowOnly() {
        when(couponService.findById(COUPON_ID)).thenReturn(newCoupon());
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertDoesNotThrow(() -> service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("49"), Collections.emptyList()));
    }

    @Test
    @DisplayName("弱断言：全部满足只断言返回非空")
    void allMet_notNullOnly() {
        when(couponService.findById(COUPON_ID)).thenReturn(newCoupon());
        when(couponUserService.findById(USER_COUPON_ID)).thenReturn(newCouponUser());
        assertNotNull(service.checkCoupon(USER_ID, COUPON_ID, USER_COUPON_ID,
                new BigDecimal("60"), Collections.emptyList()));
    }
}
