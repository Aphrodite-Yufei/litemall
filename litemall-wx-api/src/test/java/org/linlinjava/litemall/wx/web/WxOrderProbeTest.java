package org.linlinjava.litemall.wx.web;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.wx.web.support.ApiTestSupport;
import org.linlinjava.litemall.wx.web.support.WxOrderTestBase;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 探针测试：只打印真实响应与实际写入的订单金额，不做断言，用于发现缺陷并为修复提供证据。
 */
public class WxOrderProbeTest extends WxOrderTestBase {

    private void stubHappyDeps(Integer cartId, LitemallCart cart, Integer stock) throws Exception {
        when(addressService.query(uid, 1)).thenReturn(ApiTestSupport.address(1, uid));
        when(cartService.findById(cartId)).thenReturn(cart);
        when(productService.findById(cart.getProductId())).thenReturn(ApiTestSupport.product(cart.getProductId(), cart.getPrice().toPlainString(), stock));
        when(productService.reduceStock(anyInt(), any())).thenReturn(1);
        when(orderService.generateOrderSn(anyInt())).thenReturn("202611260001");
        when(orderService.add(any())).thenAnswer(inv -> {
            ((LitemallOrder) inv.getArgument(0)).setId(1001);
            return 1;
        });
    }

    @Test
    void probeIdor_foreignCartId() throws Exception {
        LitemallCart foreignCart = ApiTestSupport.cart(5, 999, 101, "100", 2); // 属于用户 999
        stubHappyDeps(5, foreignCart, 100);

        MvcResult result = ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":5,\"addressId\":1,\"couponId\":0}").andReturn();
        String resp = result.getResponse().getContentAsString();
        System.out.println("[PROBE-IDOR] userId=" + uid + " 使用 userId=999 的 cartId=5 下单，响应=" + resp);
        verify(orderService, atMostOnce()).add(any());
        System.out.println("[PROBE-IDOR] orderService.add 是否被调用(即越权下单成功)=" + mockingDetails(orderService).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("add")).count());
    }

    @Test
    void probeNegativeNumber_negativeCart() throws Exception {
        LitemallCart negativeCart = ApiTestSupport.cart(6, uid, 102, "100", -3);
        stubHappyDeps(6, negativeCart, 100);

        MvcResult result = ApiTestSupport.postJson(mvc, "/wx/order/submit", uid,
                "{\"cartId\":6,\"addressId\":1,\"couponId\":0}").andReturn();
        String resp = result.getResponse().getContentAsString();
        System.out.println("[PROBE-NEG] cart.number=-3 下单，响应=" + resp);
        long reduceStockCalls = mockingDetails(productService).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("reduceStock")).count();
        System.out.println("[PROBE-NEG] reduceStock 调用次数=" + reduceStockCalls + "（0 表示负数数量已被前置校验拦截）");
    }

    @Test
    void probeSubmitMissingField() throws Exception {
        MvcResult result = ApiTestSupport.postJson(mvc, "/wx/order/submit", uid, "{\"cartId\":1}").andReturn();
        System.out.println("[PROBE-MISS] 缺 addressId/couponId，响应=" + result.getResponse().getContentAsString());

        MvcResult badJson = ApiTestSupport.postJson(mvc, "/wx/order/submit", uid, "not-a-json").andReturn();
        System.out.println("[PROBE-BADJSON] body=not-a-json，响应=" + badJson.getResponse().getContentAsString());
    }
}
