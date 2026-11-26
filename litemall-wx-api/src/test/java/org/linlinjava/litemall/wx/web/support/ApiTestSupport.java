package org.linlinjava.litemall.wx.web.support;

import org.linlinjava.litemall.core.config.GlobalExceptionHandler;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.wx.annotation.support.LoginUserHandlerMethodArgumentResolver;
import org.linlinjava.litemall.wx.service.UserTokenManager;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 实验三接口契约测试基座。
 * <p>
 * 设计要点：
 * 1. MockMvc standalone 模式挂载真实 Controller + 真实 Service，仅对 Service 的下游依赖做 mock，
 *    这样请求会完整经过「HTTP 参数解析 → Controller → 业务校验 → 响应序列化」链路；
 * 2. 注册 LoginUserHandlerMethodArgumentResolver，使 X-Litemall-Token 能被解析为 userId（与生产一致）；
 * 3. 注册 GlobalExceptionHandler，未捕获异常会转成 errno=502，与生产环境行为一致。
 */
public final class ApiTestSupport {

    public static final String TOKEN_HEADER = LoginUserHandlerMethodArgumentResolver.LOGIN_TOKEN_KEY;
    public static final MediaType JSON = MediaType.APPLICATION_JSON;

    private ApiTestSupport() {
    }

    public static MockMvc mockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new LoginUserHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** 生成与生产同格式的 JWT，供测试模拟已登录用户 */
    public static String token(Integer userId) {
        return UserTokenManager.generateToken(userId);
    }

    /** SystemConfig 由启动时的 SystemConfigService 从数据库装载，测试中注入等价配置 */
    public static void initSystemConfig() {
        Map<String, String> configs = new HashMap<>();
        configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_VALUE, "10");
        configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_MIN, "88");
        configs.put(SystemConfig.LITEMALL_ORDER_UNPAID, "120");
        configs.put(SystemConfig.LITEMALL_ORDER_UNCONFIRM, "7");
        configs.put(SystemConfig.LITEMALL_ORDER_COMMENT, "7");
        configs.put(SystemConfig.LITEMALL_MALL_NAME, "litemall");
        ReflectionTestUtils.setField(SystemConfig.class, "SYSTEM_CONFIGS", configs);
    }

    /** 覆盖运费配置（用于构造 0 元订单等边界场景） */
    @SuppressWarnings("unchecked")
    public static void overrideFreight(String freightValue, String freightMin) {
        try {
            java.lang.reflect.Field field = SystemConfig.class.getDeclaredField("SYSTEM_CONFIGS");
            field.setAccessible(true);
            Map<String, String> configs = (Map<String, String>) field.get(null);
            configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_VALUE, freightValue);
            configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_MIN, freightMin);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ===================== 请求构造 =====================

    public static ResultActions postJson(MockMvc mvc, String path, Integer userId, String body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path).contentType(JSON).content(body == null ? "" : body);
        if (userId != null) {
            builder.header(TOKEN_HEADER, token(userId));
        }
        return mvc.perform(builder);
    }

    public static ResultActions getApi(MockMvc mvc, String path, Integer userId, String query) throws Exception {
        String url = (query == null || query.isEmpty()) ? path : path + "?" + query;
        MockHttpServletRequestBuilder builder = get(url);
        if (userId != null) {
            builder.header(TOKEN_HEADER, token(userId));
        }
        return mvc.perform(builder);
    }

    // ===================== 契约断言 =====================

    /** 统一响应契约：HTTP 200 + application/json + errno/errmsg 必填 */
    public static ResultActions assertContract(ResultActions ra) throws Exception {
        return ra.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JSON))
                .andExpect(jsonPath("$.errno").isNumber())
                .andExpect(jsonPath("$.errmsg").isString());
    }

    public static ResultActions assertErrno(ResultActions ra, int errno) throws Exception {
        return assertContract(ra).andExpect(jsonPath("$.errno").value(errno));
    }

    /** 成功响应契约：errno=0、errmsg=成功（cancel/refund 等接口成功时不返回 data） */
    public static ResultActions assertOk(ResultActions ra) throws Exception {
        return assertContract(ra)
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.errmsg").value("成功"));
    }

    /** 成功响应且必须带 data */
    public static ResultActions assertOkWithData(ResultActions ra) throws Exception {
        return assertOk(ra).andExpect(jsonPath("$.data").exists());
    }

    // ===================== 测试数据工厂 =====================

    public static LitemallAddress address(Integer id, Integer userId) {
        LitemallAddress address = new LitemallAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setName("徐宇菲");
        address.setTel("13800000000");
        address.setProvince("江西省");
        address.setCity("南昌市");
        address.setCounty("新建区");
        address.setAddressDetail("南昌航空大学前湖校区");
        address.setDeleted(false);
        return address;
    }

    public static LitemallCart cart(Integer id, Integer userId, Integer productId, String price, int number) {
        LitemallCart cart = new LitemallCart();
        cart.setId(id);
        cart.setUserId(userId);
        cart.setGoodsId(1001);
        cart.setProductId(productId);
        cart.setGoodsSn("LTM1001");
        cart.setGoodsName("测试商品");
        cart.setPicUrl("https://example.com/p.png");
        cart.setPrice(new BigDecimal(price));
        cart.setNumber((short) number);
        cart.setChecked(true);
        cart.setSpecifications(new String[]{"标准版"});
        cart.setDeleted(false);
        return cart;
    }

    public static LitemallGoodsProduct product(Integer id, String price, int stock) {
        LitemallGoodsProduct product = new LitemallGoodsProduct();
        product.setId(id);
        product.setGoodsId(1001);
        product.setPrice(new BigDecimal(price));
        product.setNumber(stock);
        product.setUrl("https://example.com/p.png");
        product.setSpecifications(new String[]{"标准版"});
        return product;
    }

    public static LitemallOrder order(Integer id, Integer userId, short status) {
        LitemallOrder order = new LitemallOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderSn("202611260001");
        order.setOrderStatus(status);
        order.setActualPrice(new BigDecimal("100.00"));
        order.setGoodsPrice(new BigDecimal("100.00"));
        order.setFreightPrice(BigDecimal.ZERO);
        order.setCouponPrice(BigDecimal.ZERO);
        order.setIntegralPrice(BigDecimal.ZERO);
        order.setOrderPrice(new BigDecimal("100.00"));
        order.setGrouponPrice(BigDecimal.ZERO);
        order.setConsignee("徐宇菲");
        order.setMobile("13800000000");
        order.setAddress("江西省南昌市新建区 南昌航空大学前湖校区");
        order.setMessage("");
        order.setComments((short) 0);
        order.setAftersaleStatus((short) 0);
        order.setAddTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(false);
        return order;
    }
}
