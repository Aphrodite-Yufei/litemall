package org.linlinjava.litemall.wx.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.wx.web.support.ApiTestSupport;
import org.linlinjava.litemall.wx.web.support.WxOrderTestBase;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 实验三：/wx/order/* 核心接口契约测试（10 个接口）。
 * <p>
 * 契约维度：HTTP 状态码、Content-Type、errno/errmsg/data 结构、各错误分支的 errno 语义。
 */
@DisplayName("订单接口契约测试")
public class WxOrderContractTest extends WxOrderTestBase {

    // ===================== POST /wx/order/submit =====================

    @Test
    @DisplayName("submit-未登录返回501")
    void submit_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/submit", null,
                "{\"cartId\":1,\"addressId\":1,\"couponId\":0}"), 501);
    }

    @Test
    @DisplayName("submit-缺少必填字段返回401")
    void submit_missingField() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":1}"), 401);
    }

    @Test
    @DisplayName("submit-非法JSON返回401")
    void submit_badJson() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "not-a-json"), 401);
    }

    @Test
    @DisplayName("submit-购物车商品不属于当前用户返回506且不下单")
    void submit_idorForeignCart() throws Exception {
        LitemallCart foreignCart = ApiTestSupport.cart(5, 999, 101, "100", 2);
        stubAddressAndCart(5, foreignCart);

        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":5,\"addressId\":1,\"couponId\":0}"), 506);
        verify(orderService, never()).add(any());
        verify(productService, never()).reduceStock(anyInt(), any());
    }

    @Test
    @DisplayName("submit-购物车数量为负数返回402且不扣库存")
    void submit_negativeNumber() throws Exception {
        LitemallCart negativeCart = ApiTestSupport.cart(6, uid, 102, "100", -3);
        stubAddressAndCart(6, negativeCart);

        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":6,\"addressId\":1,\"couponId\":0}"), 402);
        verify(orderService, never()).add(any());
        verify(productService, never()).reduceStock(anyInt(), any());
    }

    @Test
    @DisplayName("submit-购物车为空返回402")
    void submit_emptyCart() throws Exception {
        when(addressService.query(uid, 1)).thenReturn(ApiTestSupport.address(1, uid));
        when(cartService.queryByUidAndChecked(uid)).thenReturn(Collections.emptyList());
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":0,\"addressId\":1,\"couponId\":0}"), 402);
    }

    @Test
    @DisplayName("submit-收货地址不属于当前用户返回401")
    void submit_invalidAddress() throws Exception {
        when(addressService.query(uid, 1)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":1,\"addressId\":1,\"couponId\":0}"), 401);
    }

    @Test
    @DisplayName("submit-正常下单返回0且data含orderId/payed")
    void submit_ok() throws Exception {
        LitemallCart cart = ApiTestSupport.cart(1, uid, 101, "100", 2);
        stubAddressAndCart(1, cart);
        when(orderService.generateOrderSn(uid)).thenReturn("202611260001");
        when(orderService.add(any())).thenAnswer(inv -> {
            ((LitemallOrder) inv.getArgument(0)).setId(1001);
            return 1;
        });

        ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":1,\"addressId\":1,\"couponId\":0}")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.orderId").value(1001))
                .andExpect(jsonPath("$.data.payed").value(false));
        verify(productService).reduceStock(eq(101), eq((short) 2));
    }

    @Test
    @DisplayName("submit-0元订单返回payed=true")
    void submit_zeroPayOrder() throws Exception {
        // 0 元订单要求商品总价为 0 且不产生运费，故单独覆盖运费配置
        ApiTestSupport.overrideFreight("0", "0");
        LitemallCart cart = ApiTestSupport.cart(1, uid, 101, "0", 1);
        stubAddressAndCart(1, cart);
        when(orderService.generateOrderSn(uid)).thenReturn("202611260002");
        when(orderService.add(any())).thenAnswer(inv -> {
            ((LitemallOrder) inv.getArgument(0)).setId(1002);
            return 1;
        });
        // handleZeroPayOrder 会回查订单做通知
        when(orderService.findById(1002)).thenReturn(ApiTestSupport.order(1002, uid, (short) 201));

        ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":1,\"addressId\":1,\"couponId\":0}")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.payed").value(true));
        verify(orderService).updateSelective(any());
    }

    // ===================== GET /wx/order/list =====================

    @Test
    @DisplayName("list-未登录返回501")
    void list_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/order/list", null, null), 501);
    }

    @Test
    @DisplayName("list-正常返回分页契约")
    void list_ok() throws Exception {
        when(orderService.queryByOrderStatus(any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(ApiTestSupport.order(1001, uid, (short) 101)));
        when(orderGoodsService.queryByOid(1001)).thenReturn(Collections.emptyList());

        ApiTestSupport.getApi(mvc, "/wx/order/list", uid, "showType=0&page=1&limit=10&sort=add_time&order=desc")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.limit").value(1))
                .andExpect(jsonPath("$.data.pages").isNumber());
    }

    // ===================== GET /wx/order/detail =====================

    @Test
    @DisplayName("detail-未登录返回501")
    void detail_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/order/detail", null, "orderId=1001"), 501);
    }

    @Test
    @DisplayName("detail-订单不存在返回720")
    void detail_notFound() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/order/detail", uid, "orderId=1001"), 720);
    }

    @Test
    @DisplayName("detail-订单不属于当前用户返回721")
    void detail_notOwned() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, 999, (short) 101));
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/order/detail", uid, "orderId=1001"), 721);
    }

    @Test
    @DisplayName("detail-正常返回订单详情契约")
    void detail_ok() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 101));
        when(orderGoodsService.queryByOid(1001)).thenReturn(Collections.emptyList());

        ApiTestSupport.getApi(mvc, "/wx/order/detail", uid, "orderId=1001")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.orderInfo.id").value(1001))
                .andExpect(jsonPath("$.data.orderInfo.orderStatusText").value("未付款"))
                .andExpect(jsonPath("$.data.orderGoods").isArray())
                .andExpect(jsonPath("$.data.expressInfo").isArray());
    }

    // ===================== POST /wx/order/cancel =====================

    @Test
    @DisplayName("cancel-未登录返回501")
    void cancel_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/cancel", null, "{\"orderId\":1001}"), 501);
    }

    @Test
    @DisplayName("cancel-缺少orderId返回401")
    void cancel_missingOrderId() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/cancel", uid, "{}"), 401);
    }

    @Test
    @DisplayName("cancel-订单不存在返回402")
    void cancel_notFound() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/cancel", uid, "{\"orderId\":1001}"), 402);
    }

    @Test
    @DisplayName("cancel-已付款订单不可取消返回725")
    void cancel_invalidOperation() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 201));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/cancel", uid, "{\"orderId\":1001}"), 725);
    }

    @Test
    @DisplayName("cancel-正常取消返回0并恢复库存")
    void cancel_ok() throws Exception {
        LitemallOrder order = ApiTestSupport.order(1001, uid, (short) 101);
        when(orderService.findById(uid, 1001)).thenReturn(order);
        when(orderService.updateWithOptimisticLocker(any())).thenReturn(1);
        when(orderGoodsService.queryByOid(1001)).thenReturn(Collections.emptyList());
        when(couponUserService.findByOid(1001)).thenReturn(Collections.emptyList());

        ApiTestSupport.assertOk(ApiTestSupport.postJson(mvc, "/wx/order/cancel", uid, "{\"orderId\":1001}"));
    }

    // ===================== POST /wx/order/prepay =====================

    @Test
    @DisplayName("prepay-未登录返回501")
    void prepay_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/prepay", null, "{\"orderId\":1001}"), 501);
    }

    @Test
    @DisplayName("prepay-缺少orderId返回401")
    void prepay_missingOrderId() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/prepay", uid, "{}"), 401);
    }

    @Test
    @DisplayName("prepay-订单不存在返回402")
    void prepay_notFound() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/prepay", uid, "{\"orderId\":1001}"), 402);
    }

    @Test
    @DisplayName("prepay-订单非待付款状态返回725")
    void prepay_invalidOperation() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 301));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/prepay", uid, "{\"orderId\":1001}"), 725);
    }

    @Test
    @DisplayName("prepay-微信支付异常返回724")
    void prepay_payFail() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 101));
        LitemallUser user = new LitemallUser();
        user.setWeixinOpenid("oX-test-openid");
        when(userService.findById(uid)).thenReturn(user);
        when(wxPayService.createOrder(any())).thenThrow(new RuntimeException("mch key invalid"));

        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/prepay", uid, "{\"orderId\":1001}"), 724);
    }

    // ===================== POST /wx/order/refund =====================

    @Test
    @DisplayName("refund-未登录返回501")
    void refund_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/refund", null, "{\"orderId\":1001}"), 501);
    }

    @Test
    @DisplayName("refund-缺少orderId返回401")
    void refund_missingOrderId() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/refund", uid, "{}"), 401);
    }

    @Test
    @DisplayName("refund-订单不存在返回401")
    void refund_notFound() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/refund", uid, "{\"orderId\":1001}"), 401);
    }

    @Test
    @DisplayName("refund-未付款订单不可退款返回725")
    void refund_invalidOperation() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 101));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/refund", uid, "{\"orderId\":1001}"), 725);
    }

    @Test
    @DisplayName("refund-已付款订单申请退款返回0")
    void refund_ok() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 201));
        when(orderService.updateWithOptimisticLocker(any())).thenReturn(1);
        ApiTestSupport.assertOk(ApiTestSupport.postJson(mvc, "/wx/order/refund", uid, "{\"orderId\":1001}"));
    }

    // ===================== POST /wx/order/confirm =====================

    @Test
    @DisplayName("confirm-未登录返回501")
    void confirm_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/confirm", null, "{\"orderId\":1001}"), 501);
    }

    @Test
    @DisplayName("confirm-订单不存在返回401")
    void confirm_notFound() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/confirm", uid, "{\"orderId\":1001}"), 401);
    }

    @Test
    @DisplayName("confirm-未发货订单不可确认返回725")
    void confirm_invalidOperation() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 201));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/confirm", uid, "{\"orderId\":1001}"), 725);
    }

    @Test
    @DisplayName("confirm-已发货订单确认收货返回0")
    void confirm_ok() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 301));
        when(orderGoodsService.getComments(1001)).thenReturn((short) 0);
        when(orderService.updateWithOptimisticLocker(any())).thenReturn(1);
        ApiTestSupport.assertOk(ApiTestSupport.postJson(mvc, "/wx/order/confirm", uid, "{\"orderId\":1001}"));
    }

    // ===================== POST /wx/order/delete =====================

    @Test
    @DisplayName("delete-未登录返回501")
    void delete_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/delete", null, "{\"orderId\":1001}"), 501);
    }

    @Test
    @DisplayName("delete-订单不存在返回401")
    void delete_notFound() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/delete", uid, "{\"orderId\":1001}"), 401);
    }

    @Test
    @DisplayName("delete-未取消订单不可删除返回725")
    void delete_invalidOperation() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 101));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/delete", uid, "{\"orderId\":1001}"), 725);
    }

    @Test
    @DisplayName("delete-已取消订单删除返回0")
    void delete_ok() throws Exception {
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 102));
        ApiTestSupport.assertOk(ApiTestSupport.postJson(mvc, "/wx/order/delete", uid, "{\"orderId\":1001}"));
        verify(orderService).deleteById(1001);
    }

    // ===================== GET /wx/order/goods =====================

    @Test
    @DisplayName("goods-未登录返回501")
    void goods_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/order/goods", null, "ogid=1"), 501);
    }

    @Test
    @DisplayName("goods-订单不存在返回401而非502崩溃")
    void goods_notFoundOrder() throws Exception {
        LitemallOrderGoods orderGoods = orderGoods(1, 1001);
        when(orderGoodsService.findById(1)).thenReturn(orderGoods);
        when(orderService.findById(uid, 1001)).thenReturn(null);

        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/order/goods", uid, "ogid=1"), 401);
    }

    @Test
    @DisplayName("goods-订单不属于当前用户返回401")
    void goods_notOwned() throws Exception {
        when(orderGoodsService.findById(1)).thenReturn(orderGoods(1, 1001));
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, 999, (short) 401));

        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/order/goods", uid, "ogid=1"), 401);
    }

    @Test
    @DisplayName("goods-正常返回订单商品")
    void goods_ok() throws Exception {
        when(orderGoodsService.findById(1)).thenReturn(orderGoods(1, 1001));
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 401));

        ApiTestSupport.getApi(mvc, "/wx/order/goods", uid, "ogid=1")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // ===================== POST /wx/order/comment =====================

    @Test
    @DisplayName("comment-未登录返回501")
    void comment_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/comment", null,
                "{\"orderGoodsId\":1,\"star\":5}"), 501);
    }

    @Test
    @DisplayName("comment-缺少orderGoodsId返回401")
    void comment_missingOrderGoodsId() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/comment", uid, "{\"star\":5}"), 401);
    }

    @Test
    @DisplayName("comment-订单商品不存在返回402")
    void comment_orderGoodsNotFound() throws Exception {
        when(orderGoodsService.findById(1)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/comment", uid,
                "{\"orderGoodsId\":1,\"star\":5}"), 402);
    }

    @Test
    @DisplayName("comment-订单状态不允许评价返回725")
    void comment_invalidStatus() throws Exception {
        when(orderGoodsService.findById(1)).thenReturn(orderGoods(1, 1001));
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 101));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/comment", uid,
                "{\"orderGoodsId\":1,\"star\":5}"), 725);
    }

    @Test
    @DisplayName("comment-星级越界返回402")
    void comment_starOutOfRange() throws Exception {
        when(orderGoodsService.findById(1)).thenReturn(orderGoods(1, 1001));
        when(orderService.findById(uid, 1001)).thenReturn(ApiTestSupport.order(1001, uid, (short) 401));
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/order/comment", uid,
                "{\"orderGoodsId\":1,\"star\":6}"), 402);
    }

    @Test
    @DisplayName("comment-正常评价返回0")
    void comment_ok() throws Exception {
        LitemallOrderGoods orderGoods = orderGoods(1, 1001);
        LitemallOrder order = ApiTestSupport.order(1001, uid, (short) 401);
        order.setComments((short) 1);
        when(orderGoodsService.findById(1)).thenReturn(orderGoods);
        when(orderService.findById(uid, 1001)).thenReturn(order);
        when(orderService.updateWithOptimisticLocker(any())).thenReturn(1);

        ApiTestSupport.assertOk(ApiTestSupport.postJson(mvc, "/wx/order/comment", uid,
                "{\"orderGoodsId\":1,\"star\":5,\"content\":\"很好\"}"));
        verify(commentService).save(any());
        verify(orderGoodsService).updateById(any());
    }

    // ===================== helpers =====================

    private void stubAddressAndCart(Integer cartId, LitemallCart cart) {
        when(addressService.query(uid, 1)).thenReturn(ApiTestSupport.address(1, uid));
        when(cartService.findById(cartId)).thenReturn(cart);
        when(productService.findById(cart.getProductId()))
                .thenReturn(ApiTestSupport.product(cart.getProductId(), "100", 100));
        when(productService.reduceStock(anyInt(), any())).thenReturn(1);
    }

    private LitemallOrderGoods orderGoods(Integer id, Integer orderId) {
        LitemallOrderGoods orderGoods = new LitemallOrderGoods();
        orderGoods.setId(id);
        orderGoods.setOrderId(orderId);
        orderGoods.setGoodsId(1001);
        orderGoods.setGoodsName("测试商品");
        orderGoods.setNumber((short) 1);
        orderGoods.setPrice(new java.math.BigDecimal("100.00"));
        orderGoods.setComment(0);
        return orderGoods;
    }
}
