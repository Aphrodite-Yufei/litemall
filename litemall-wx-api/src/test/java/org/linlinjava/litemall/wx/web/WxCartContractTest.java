package org.linlinjava.litemall.wx.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallAddressService;
import org.linlinjava.litemall.db.service.LitemallCartService;
import org.linlinjava.litemall.db.service.LitemallCouponService;
import org.linlinjava.litemall.db.service.LitemallCouponUserService;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallGrouponRulesService;
import org.linlinjava.litemall.db.service.CouponVerifyService;
import org.linlinjava.litemall.wx.web.support.ApiTestSupport;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 购物车模块 8 个接口的契约测试（实验三）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WxCartContractTest {

    private static final Integer UID = 1;

    @Mock
    private LitemallCartService cartService;
    @Mock
    private LitemallGoodsService goodsService;
    @Mock
    private LitemallGoodsProductService productService;
    @Mock
    private LitemallAddressService addressService;
    @Mock
    private LitemallGrouponRulesService grouponRulesService;
    @Mock
    private LitemallCouponService couponService;
    @Mock
    private LitemallCouponUserService couponUserService;
    @Mock
    private CouponVerifyService couponVerifyService;

    @InjectMocks
    private WxCartController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ApiTestSupport.initSystemConfig();
        mvc = ApiTestSupport.mockMvc(controller);
    }

    private LitemallGoods goods(boolean onSale) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(1001);
        goods.setName("测试商品");
        goods.setGoodsSn("LTM1001");
        goods.setPicUrl("https://example.com/p.png");
        goods.setIsOnSale(onSale);
        goods.setDeleted(false);
        return goods;
    }

    // ===================== GET /wx/cart/index =====================

    @Test
    @DisplayName("index-未登录返回501")
    void index_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/cart/index", null, null), 501);
    }

    @Test
    @DisplayName("index-正常返回cartList与cartTotal契约")
    void index_ok() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(true));
        when(cartService.queryByUid(UID)).thenReturn(Collections.singletonList(
                ApiTestSupport.cart(1, UID, 11, "10.00", 2)));

        ApiTestSupport.getApi(mvc, "/wx/cart/index", UID, null)
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.cartList").isArray())
                .andExpect(jsonPath("$.data.cartList[0].id").value(1))
                .andExpect(jsonPath("$.data.cartTotal.goodsCount").value(2))
                .andExpect(jsonPath("$.data.cartTotal.goodsAmount").value(20.00))
                .andExpect(jsonPath("$.data.cartTotal.checkedGoodsCount").value(2))
                .andExpect(jsonPath("$.data.cartTotal.checkedGoodsAmount").value(20.00));
    }

    @Test
    @DisplayName("index-商品下架自动剔除购物车项")
    void index_offsale() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(false));
        when(cartService.queryByUid(UID)).thenReturn(Collections.singletonList(
                ApiTestSupport.cart(1, UID, 11, "10.00", 2)));

        ApiTestSupport.getApi(mvc, "/wx/cart/index", UID, null)
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.cartList.length()").value(0))
                .andExpect(jsonPath("$.data.cartTotal.goodsCount").value(0));
    }

    // ===================== POST /wx/cart/add =====================

    @Test
    @DisplayName("add-未登录返回501")
    void add_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/add", null,
                "{\"goodsId\":1001,\"productId\":11,\"number\":2}"), 501);
    }

    @Test
    @DisplayName("add-缺少number返回401而非502崩溃")
    void add_missingNumber() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/add", UID,
                "{\"goodsId\":1001,\"productId\":11}"), 401);
    }

    @Test
    @DisplayName("add-数量为负数返回401")
    void add_negativeNumber() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/add", UID,
                "{\"goodsId\":1001,\"productId\":11,\"number\":-3}"), 401);
    }

    @Test
    @DisplayName("add-商品已下架返回710")
    void add_offsale() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(false));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/add", UID,
                "{\"goodsId\":1001,\"productId\":11,\"number\":2}"), 710);
    }

    @Test
    @DisplayName("add-库存不足返回711")
    void add_noStock() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(true));
        when(productService.findById(11)).thenReturn(ApiTestSupport.product(11, "10.00", 1));
        when(cartService.queryExist(1001, 11, UID)).thenReturn(null);

        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/add", UID,
                "{\"goodsId\":1001,\"productId\":11,\"number\":2}"), 711);
    }

    @Test
    @DisplayName("add-成功返回购物车商品数量")
    void add_ok() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(true));
        when(productService.findById(11)).thenReturn(ApiTestSupport.product(11, "10.00", 100));
        when(cartService.queryExist(1001, 11, UID)).thenReturn(null);
        doAnswer(inv -> {
            ((LitemallCart) inv.getArgument(0)).setId(55);
            return null;
        }).when(cartService).add(any(LitemallCart.class));
        when(cartService.queryByUid(UID)).thenReturn(Arrays.asList(
                ApiTestSupport.cart(55, UID, 11, "10.00", 2)));

        ApiTestSupport.postJson(mvc, "/wx/cart/add", UID,
                        "{\"goodsId\":1001,\"productId\":11,\"number\":2}")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data").value(2));
    }

    // ===================== POST /wx/cart/fastadd =====================

    @Test
    @DisplayName("fastadd-未登录返回501")
    void fastadd_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/fastadd", null,
                "{\"goodsId\":1001,\"productId\":11,\"number\":1}"), 501);
    }

    @Test
    @DisplayName("fastadd-缺少number返回401而非502崩溃")
    void fastadd_missingNumber() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/fastadd", UID,
                "{\"goodsId\":1001,\"productId\":11}"), 401);
    }

    @Test
    @DisplayName("fastadd-成功返回购物车项ID")
    void fastadd_ok() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(true));
        when(productService.findById(11)).thenReturn(ApiTestSupport.product(11, "10.00", 100));
        when(cartService.queryExist(1001, 11, UID)).thenReturn(null);
        doAnswer(inv -> {
            ((LitemallCart) inv.getArgument(0)).setId(77);
            return null;
        }).when(cartService).add(any(LitemallCart.class));

        ApiTestSupport.postJson(mvc, "/wx/cart/fastadd", UID,
                        "{\"goodsId\":1001,\"productId\":11,\"number\":1}")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data").value(77));
    }

    // ===================== POST /wx/cart/update =====================

    @Test
    @DisplayName("update-未登录返回501")
    void update_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/update", null,
                "{\"id\":1,\"goodsId\":1001,\"productId\":11,\"number\":1}"), 501);
    }

    @Test
    @DisplayName("update-缺少number返回401而非502崩溃")
    void update_missingNumber() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/update", UID,
                "{\"id\":1,\"goodsId\":1001,\"productId\":11}"), 401);
    }

    @Test
    @DisplayName("update-购物车项不存在返回402")
    void update_notFound() throws Exception {
        when(cartService.findById(UID, 1)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/update", UID,
                "{\"id\":1,\"goodsId\":1001,\"productId\":11,\"number\":3}"), 402);
    }

    @Test
    @DisplayName("update-越权访问他人购物车项返回402")
    void update_otherUserCart() throws Exception {
        // cartService.findById(userId,id) 带归属校验，他人购物车返回 null
        when(cartService.findById(UID, 999)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/update", UID,
                "{\"id\":999,\"goodsId\":1001,\"productId\":11,\"number\":3}"), 402);
    }

    @Test
    @DisplayName("update-成功返回无data的成功响应")
    void update_ok() throws Exception {
        when(cartService.findById(UID, 5)).thenReturn(ApiTestSupport.cart(5, UID, 11, "10.00", 2));
        when(goodsService.findById(1001)).thenReturn(goods(true));
        when(productService.findById(11)).thenReturn(ApiTestSupport.product(11, "10.00", 100));
        when(cartService.updateById(any(LitemallCart.class))).thenReturn(1);

        ApiTestSupport.assertOk(ApiTestSupport.postJson(mvc, "/wx/cart/update", UID,
                "{\"id\":5,\"goodsId\":1001,\"productId\":11,\"number\":3}"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ===================== POST /wx/cart/checked =====================

    @Test
    @DisplayName("checked-未登录返回501")
    void checked_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/checked", null,
                "{\"productIds\":[11],\"isChecked\":1}"), 501);
    }

    @Test
    @DisplayName("checked-缺少productIds返回401")
    void checked_missingIds() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/checked", UID,
                "{\"isChecked\":1}"), 401);
    }

    @Test
    @DisplayName("checked-缺少isChecked返回401")
    void checked_missingChecked() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/checked", UID,
                "{\"productIds\":[11]}"), 401);
    }

    @Test
    @DisplayName("checked-成功返回购物车全量结构")
    void checked_ok() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(true));
        when(cartService.queryByUid(UID)).thenReturn(Collections.singletonList(
                ApiTestSupport.cart(1, UID, 11, "10.00", 2)));

        ApiTestSupport.postJson(mvc, "/wx/cart/checked", UID,
                        "{\"productIds\":[11],\"isChecked\":1}")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.cartList").isArray())
                .andExpect(jsonPath("$.data.cartTotal.goodsCount").isNumber());
    }

    // ===================== POST /wx/cart/delete =====================

    @Test
    @DisplayName("delete-未登录返回501")
    void delete_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/delete", null,
                "{\"productIds\":[11]}"), 501);
    }

    @Test
    @DisplayName("delete-productIds为空返回401")
    void delete_emptyIds() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/cart/delete", UID,
                "{\"productIds\":[]}"), 401);
    }

    @Test
    @DisplayName("delete-成功返回购物车全量结构")
    void delete_ok() throws Exception {
        when(goodsService.findById(1001)).thenReturn(goods(true));
        when(cartService.queryByUid(UID)).thenReturn(new ArrayList<>());

        ApiTestSupport.postJson(mvc, "/wx/cart/delete", UID, "{\"productIds\":[11]}")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.cartList.length()").value(0))
                .andExpect(jsonPath("$.data.cartTotal.goodsCount").value(0));
    }

    // ===================== GET /wx/cart/goodscount =====================

    @Test
    @DisplayName("goodscount-未登录返回0而非501")
    void goodscount_unlogin() throws Exception {
        // 契约现状：未登录不报错，返回 errno=0、data=0
        ApiTestSupport.getApi(mvc, "/wx/cart/goodscount", null, null)
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("goodscount-登录返回商品总件数")
    void goodscount_ok() throws Exception {
        when(cartService.queryByUid(UID)).thenReturn(Arrays.asList(
                ApiTestSupport.cart(1, UID, 11, "10.00", 2),
                ApiTestSupport.cart(2, UID, 12, "5.00", 3)));

        ApiTestSupport.getApi(mvc, "/wx/cart/goodscount", UID, null)
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    // ===================== GET /wx/cart/checkout =====================

    @Test
    @DisplayName("checkout-未登录返回501")
    void checkout_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/cart/checkout", null,
                "cartId=0&addressId=0&couponId=0"), 501);
    }

    @Test
    @DisplayName("checkout-指定不存在的购物车项返回402")
    void checkout_cartNotFound() throws Exception {
        when(cartService.findById(UID, 999)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/cart/checkout", UID,
                "cartId=999&addressId=1&couponId=-1"), 402);
    }

    @Test
    @DisplayName("checkout-正常返回结算金额结构")
    void checkout_ok() throws Exception {
        when(addressService.query(UID, 1)).thenReturn(ApiTestSupport.address(1, UID));
        when(cartService.findById(UID, 5)).thenReturn(ApiTestSupport.cart(5, UID, 11, "10.00", 2));
        when(couponUserService.queryAll(UID)).thenReturn(Collections.emptyList());

        ApiTestSupport.getApi(mvc, "/wx/cart/checkout", UID, "cartId=5&addressId=1&couponId=-1")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.addressId").value(1))
                .andExpect(jsonPath("$.data.goodsTotalPrice").value(20.00))
                .andExpect(jsonPath("$.data.freightPrice").value(10.00))
                .andExpect(jsonPath("$.data.orderTotalPrice").value(30.00))
                .andExpect(jsonPath("$.data.actualPrice").value(30.00))
                .andExpect(jsonPath("$.data.checkedGoodsList").isArray())
                .andExpect(jsonPath("$.data.availableCouponLength").value(0));
    }

    @Test
    @DisplayName("checkout-无收货地址时回退为addressId=0")
    void checkout_noAddress() throws Exception {
        when(addressService.findDefault(UID)).thenReturn(null);
        when(cartService.queryByUidAndChecked(UID)).thenReturn(Collections.emptyList());
        when(couponUserService.queryAll(UID)).thenReturn(Collections.emptyList());

        ApiTestSupport.getApi(mvc, "/wx/cart/checkout", UID, "cartId=0&addressId=0&couponId=-1")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.addressId").value(0))
                .andExpect(jsonPath("$.data.checkedAddress.id").value(0))
                // 契约现状（缺陷候选）：购物车为空时仍按未达免运费门槛计 10 元运费
                .andExpect(jsonPath("$.data.freightPrice").value(10.00))
                .andExpect(jsonPath("$.data.orderTotalPrice").value(10.00));
    }
}
