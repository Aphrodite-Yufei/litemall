package org.linlinjava.litemall.wx.web;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.config.GlobalExceptionHandler;
import org.linlinjava.litemall.wx.annotation.support.LoginUserHandlerMethodArgumentResolver;
import org.linlinjava.litemall.wx.service.WxOrderService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmokeTest {

    @Test
    void submitWithoutLoginReturns501() throws Exception {
        WxOrderController controller = new WxOrderController();
        ReflectionTestUtils.setField(controller, "wxOrderService", new WxOrderService());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new LoginUserHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/wx/order/submit")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(501));
    }
}
