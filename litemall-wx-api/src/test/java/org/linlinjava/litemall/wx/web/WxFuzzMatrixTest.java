package org.linlinjava.litemall.wx.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallAddress;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.wx.web.support.ApiTestSupport;
import org.linlinjava.litemall.wx.web.support.WxOrderTestBase;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 四类语义 Fuzzing 载荷矩阵（实验三）。
 * <p>
 * 类型混淆 / 极端数值 / 安全攻击（IDOR、SQL 注入）/ 编码异常 四类载荷，
 * 覆盖订单提交、购物车增改、订单详情、地址详情与保存等高风险接口，
 * 逐条记录 HTTP 状态、业务 errno 与判定结论，落盘为 CSV 供报告引用。
 */
class WxFuzzMatrixTest extends WxOrderTestBase {

    @Mock
    private LitemallGoodsService goodsService;

    private MockMvc cartMvc;
    private MockMvc addressMvc;

    private static final List<String[]> ROWS = new ArrayList<>();
    private static final Pattern ERRNO = Pattern.compile("\"errno\"\\s*:\\s*(-?\\d+)");

    private static final String T_TYPE = "类型混淆";
    private static final String T_EXTREME = "极端数值";
    private static final String T_SECURITY = "安全攻击";
    private static final String T_ENCODING = "编码异常";

    @BeforeEach
    void setUpFuzz() {
        WxCartController cartController = new WxCartController();
        ReflectionTestUtils.setField(cartController, "cartService", cartService);
        ReflectionTestUtils.setField(cartController, "goodsService", goodsService);
        ReflectionTestUtils.setField(cartController, "productService", productService);
        ReflectionTestUtils.setField(cartController, "addressService", addressService);
        ReflectionTestUtils.setField(cartController, "grouponRulesService", grouponRulesService);
        ReflectionTestUtils.setField(cartController, "couponService", couponService);
        ReflectionTestUtils.setField(cartController, "couponUserService", couponUserService);
        ReflectionTestUtils.setField(cartController, "couponVerifyService", couponVerifyService);
        cartMvc = ApiTestSupport.mockMvc(cartController);

        WxAddressController addressController = new WxAddressController();
        ReflectionTestUtils.setField(addressController, "addressService", addressService);
        ReflectionTestUtils.setField(addressController, "regionService", regionService);
        addressMvc = ApiTestSupport.mockMvc(addressController);
    }

    // ===================== 记录与判定 =====================

    private interface Call {
        ResultActions run() throws Exception;
    }

    private void probe(String endpoint, String category, String payload, Call call) {
        try {
            MvcResult result = call.run().andReturn();
            int status = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();
            Matcher m = ERRNO.matcher(body == null ? "" : body);
            int errno = m.find() ? Integer.parseInt(m.group(1)) : -999;
            ROWS.add(new String[]{endpoint, category, payload, String.valueOf(status),
                    String.valueOf(errno), verdict(category, status, errno)});
        } catch (Exception e) {
            ROWS.add(new String[]{endpoint, category, payload, "EXCEPTION", "-999", "测试执行异常"});
        }
    }

    private static String verdict(String category, int status, int errno) {
        if (status != 200) {
            return "请求层拒绝(HTTP " + status + ")";
        }
        if (errno == 502) {
            return "未捕获异常(502)";
        }
        if (errno == -999) {
            return "响应非标准JSON";
        }
        if (errno == 0) {
            return T_SECURITY.equals(category) ? "越权/注入被受理(缺陷)" : "正常受理";
        }
        if (errno == 401 || errno == 402 || errno == 506) {
            return "契约内拒绝";
        }
        if (errno == 710 || errno == 711 || errno == 720 || errno == 721 || errno == 725) {
            return "业务码拒绝";
        }
        return "其他errno";
    }

    private Call submit(String body) {
        return () -> ApiTestSupport.postJson(mvc, "/wx/order/submit", uid, body);
    }

    private Call add(String body) {
        return () -> ApiTestSupport.postJson(cartMvc, "/wx/cart/add", uid, body);
    }

    private Call update(String body) {
        return () -> ApiTestSupport.postJson(cartMvc, "/wx/cart/update", uid, body);
    }

    private void baseSubmitStubs() {
        when(addressService.query(anyInt(), anyInt())).thenReturn(ApiTestSupport.address(1, uid));
        when(addressService.findDefault(anyInt())).thenReturn(ApiTestSupport.address(1, uid));
        when(cartService.queryByUidAndChecked(anyInt()))
                .thenReturn(Arrays.asList(ApiTestSupport.cart(5, uid, 11, "10.00", 2)));
        when(couponUserService.queryAll(anyInt())).thenReturn(new ArrayList<>());
        when(productService.findById(anyInt())).thenReturn(ApiTestSupport.product(11, "10.00", 100));
        when(productService.reduceStock(anyInt(), org.mockito.ArgumentMatchers.any(Short.class))).thenReturn(1);
        doAnswer(inv -> {
            ((org.linlinjava.litemall.db.domain.LitemallOrder) inv.getArgument(0)).setId(1001);
            return null;
        }).when(orderService).add(any(org.linlinjava.litemall.db.domain.LitemallOrder.class));
    }

    private org.linlinjava.litemall.db.domain.LitemallGoods goods() {
        org.linlinjava.litemall.db.domain.LitemallGoods goods = new org.linlinjava.litemall.db.domain.LitemallGoods();
        goods.setId(1001);
        goods.setName("测试商品");
        goods.setGoodsSn("LTM1001");
        goods.setPicUrl("https://example.com/p.png");
        goods.setIsOnSale(true);
        goods.setDeleted(false);
        return goods;
    }

    // ===================== 订单提交 =====================

    @Test
    @DisplayName("submit-类型混淆")
    void submit_typeConfusion() throws Exception {
        baseSubmitStubs();
        probe("/wx/order/submit", T_TYPE, "cartId=\"0\"(字符串)", submit("{\"cartId\":\"0\",\"addressId\":1,\"couponId\":0}"));
        probe("/wx/order/submit", T_TYPE, "addressId=\"abc\"", submit("{\"cartId\":0,\"addressId\":\"abc\",\"couponId\":0}"));
        probe("/wx/order/submit", T_TYPE, "cartId=[0](数组)", submit("{\"cartId\":[0],\"addressId\":1,\"couponId\":0}"));
        probe("/wx/order/submit", T_TYPE, "全字段null", submit("{\"cartId\":null,\"addressId\":null,\"couponId\":null}"));
        probe("/wx/order/submit", T_TYPE, "addressId={\"id\":1}(对象)", submit("{\"cartId\":0,\"addressId\":{\"id\":1},\"couponId\":0}"));
        probe("/wx/order/submit", T_TYPE, "空JSON {}", submit("{}"));
    }

    @Test
    @DisplayName("submit-极端数值")
    void submit_extreme() throws Exception {
        baseSubmitStubs();
        probe("/wx/order/submit", T_EXTREME, "cartId=Integer.MAX_VALUE", submit("{\"cartId\":2147483647,\"addressId\":1,\"couponId\":0}"));
        probe("/wx/order/submit", T_EXTREME, "cartId=Integer.MIN_VALUE", submit("{\"cartId\":-2147483648,\"addressId\":1,\"couponId\":0}"));
        probe("/wx/order/submit", T_EXTREME, "addressId=999999999", submit("{\"cartId\":0,\"addressId\":999999999,\"couponId\":0}"));
        probe("/wx/order/submit", T_EXTREME, "couponId=Integer.MAX_VALUE", submit("{\"cartId\":0,\"addressId\":1,\"couponId\":2147483647}"));
        probe("/wx/order/submit", T_EXTREME, "cartId=-1", submit("{\"cartId\":-1,\"addressId\":1,\"couponId\":0}"));
    }

    @Test
    @DisplayName("submit-安全攻击(IDOR)")
    void submit_idor() throws Exception {
        baseSubmitStubs();
        // cartId=5 属于 userId=999 的其他用户
        when(cartService.findById(5)).thenReturn(ApiTestSupport.cart(5, 999, 11, "10.00", 2));
        probe("/wx/order/submit", T_SECURITY, "IDOR:使用他人cartId=5下单",
                submit("{\"cartId\":5,\"addressId\":1,\"couponId\":0}"));

        // 他人收货地址
        when(addressService.query(uid, 2)).thenReturn(null);
        probe("/wx/order/submit", T_SECURITY, "IDOR:使用他人addressId=2",
                submit("{\"cartId\":0,\"addressId\":2,\"couponId\":0}"));

        // 负数数量购物车（0元订单链）
        when(cartService.queryByUidAndChecked(uid))
                .thenReturn(Arrays.asList(ApiTestSupport.cart(6, uid, 11, "10.00", -3)));
        // 货品已删除：修复前 deductStock 抛 RuntimeException → 502
        when(productService.findById(11)).thenReturn(null);
        probe("/wx/order/submit", T_SECURITY, "货品已删除(下单引用失效productId)",
                submit("{\"cartId\":0,\"addressId\":1,\"couponId\":0}"));
        when(productService.findById(11)).thenReturn(ApiTestSupport.product(11, "10.00", 100));

        probe("/wx/order/submit", T_SECURITY, "负数量购物车(number=-3)下单",
                submit("{\"cartId\":0,\"addressId\":1,\"couponId\":0}"));
    }

    @Test
    @DisplayName("submit-编码异常")
    void submit_encoding() throws Exception {
        baseSubmitStubs();
        probe("/wx/order/submit", T_ENCODING, "UTF-8 BOM前缀",
                submit("\uFEFF{\"cartId\":0,\"addressId\":1,\"couponId\":0}"));
        probe("/wx/order/submit", T_ENCODING, "含NUL字符",
                submit("{\"cartId\":0,\"addressId\":1,\"couponId\":0}\u0000"));
        probe("/wx/order/submit", T_ENCODING, "JSON尾部截断",
                submit("{\"cartId\":0,\"addressId\":1"));
        probe("/wx/order/submit", T_ENCODING, "重复字段cartId",
                submit("{\"cartId\":5,\"cartId\":0,\"addressId\":1,\"couponId\":0}"));
    }

    // ===================== 购物车加入 =====================

    @Test
    @DisplayName("cart/add-类型混淆")
    void cartAdd_typeConfusion() throws Exception {
        when(goodsService.findById(anyInt())).thenReturn(goods());
        when(productService.findById(anyInt())).thenReturn(ApiTestSupport.product(11, "10.00", 100));
        probe("/wx/cart/add", T_TYPE, "number=\"2\"(字符串)", add("{\"goodsId\":1001,\"productId\":11,\"number\":\"2\"}"));
        probe("/wx/cart/add", T_TYPE, "goodsId=\"1001\"", add("{\"goodsId\":\"1001\",\"productId\":11,\"number\":2}"));
        probe("/wx/cart/add", T_TYPE, "number=2.5(小数)", add("{\"goodsId\":1001,\"productId\":11,\"number\":2.5}"));
        probe("/wx/cart/add", T_TYPE, "goodsId=[1001]", add("{\"goodsId\":[1001],\"productId\":11,\"number\":2}"));
        probe("/wx/cart/add", T_TYPE, "number=null", add("{\"goodsId\":1001,\"productId\":11,\"number\":null}"));
    }

    @Test
    @DisplayName("cart/add-极端数值")
    void cartAdd_extreme() throws Exception {
        when(goodsService.findById(anyInt())).thenReturn(goods());
        when(productService.findById(anyInt())).thenReturn(ApiTestSupport.product(11, "10.00", 100));
        probe("/wx/cart/add", T_EXTREME, "number=32768(Short溢出)", add("{\"goodsId\":1001,\"productId\":11,\"number\":32768}"));
        probe("/wx/cart/add", T_EXTREME, "number=32767", add("{\"goodsId\":1001,\"productId\":11,\"number\":32767}"));
        probe("/wx/cart/add", T_EXTREME, "number=Integer.MAX_VALUE", add("{\"goodsId\":1001,\"productId\":11,\"number\":2147483647}"));
        probe("/wx/cart/add", T_EXTREME, "number=-1", add("{\"goodsId\":1001,\"productId\":11,\"number\":-1}"));
        probe("/wx/cart/add", T_EXTREME, "productId=0", add("{\"goodsId\":1001,\"productId\":0,\"number\":2}"));
    }

    @Test
    @DisplayName("cart/update-类型混淆与极端数值")
    void cartUpdate_mixed() throws Exception {
        // 精确 stub：仅 id=5 属于当前用户，其余 id（含 999/MIN_VALUE）均不存在
        when(cartService.findById(uid, 5)).thenReturn(ApiTestSupport.cart(5, uid, 11, "10.00", 2));
        when(goodsService.findById(anyInt())).thenReturn(goods());
        when(productService.findById(anyInt())).thenReturn(ApiTestSupport.product(11, "10.00", 100));
        when(cartService.updateById(any())).thenReturn(1);
        when(cartService.findById(uid, 2147483647)).thenReturn(null);

        probe("/wx/cart/update", T_TYPE, "number=\"3\"", update("{\"id\":5,\"goodsId\":1001,\"productId\":11,\"number\":\"3\"}"));
        probe("/wx/cart/update", T_TYPE, "id=true(布尔)", update("{\"id\":true,\"goodsId\":1001,\"productId\":11,\"number\":3}"));
        probe("/wx/cart/update", T_EXTREME, "number=32768", update("{\"id\":5,\"goodsId\":1001,\"productId\":11,\"number\":32768}"));
        probe("/wx/cart/update", T_EXTREME, "id=Integer.MIN_VALUE", update("{\"id\":-2147483648,\"goodsId\":1001,\"productId\":11,\"number\":3}"));
        probe("/wx/cart/update", T_SECURITY, "IDOR:修改他人cartId=999", update("{\"id\":999,\"goodsId\":1001,\"productId\":11,\"number\":3}"));
    }

    @Test
    @DisplayName("cart/checked-注入与类型混淆")
    void cartChecked_injection() throws Exception {
        when(goodsService.findById(anyInt())).thenReturn(goods());
        when(cartService.queryByUid(anyInt())).thenReturn(new ArrayList<>());

        probe("/wx/cart/checked", T_SECURITY, "productIds含SQL注入串",
                () -> ApiTestSupport.postJson(cartMvc, "/wx/cart/checked", uid,
                        "{\"productIds\":[\"1 OR 1=1\"],\"isChecked\":1}"));
        probe("/wx/cart/checked", T_TYPE, "isChecked=\"1\"(字符串)",
                () -> ApiTestSupport.postJson(cartMvc, "/wx/cart/checked", uid,
                        "{\"productIds\":[11],\"isChecked\":\"1\"}"));
        probe("/wx/cart/checked", T_EXTREME, "productIds=[2147483647]",
                () -> ApiTestSupport.postJson(cartMvc, "/wx/cart/checked", uid,
                        "{\"productIds\":[2147483647],\"isChecked\":1}"));
    }

    // ===================== 订单详情与取消 =====================

    @Test
    @DisplayName("order/detail-类型混淆与注入")
    void orderDetail_mixed() throws Exception {
        probe("/wx/order/detail", T_TYPE, "orderId=abc", () -> ApiTestSupport.getApi(mvc, "/wx/order/detail", uid, "orderId=abc"));
        probe("/wx/order/detail", T_SECURITY, "orderId=1 OR 1=1", () -> ApiTestSupport.getApi(mvc, "/wx/order/detail", uid, "orderId=1 OR 1=1"));
        probe("/wx/order/detail", T_EXTREME, "orderId=2147483647", () -> ApiTestSupport.getApi(mvc, "/wx/order/detail", uid, "orderId=2147483647"));
        probe("/wx/order/detail", T_EXTREME, "orderId=-1", () -> ApiTestSupport.getApi(mvc, "/wx/order/detail", uid, "orderId=-1"));
    }

    @Test
    @DisplayName("order/cancel-类型混淆与注入")
    void orderCancel_mixed() throws Exception {
        probe("/wx/order/cancel", T_TYPE, "orderId=\"1001\"(字符串)",
                () -> ApiTestSupport.postJson(mvc, "/wx/order/cancel", uid, "{\"orderId\":\"1001\"}"));
        probe("/wx/order/cancel", T_SECURITY, "orderId=1';DROP TABLE(注入)",
                () -> ApiTestSupport.postJson(mvc, "/wx/order/cancel", uid, "{\"orderId\":\"1';DROP TABLE litemall_order;--\"}"));
        probe("/wx/order/cancel", T_EXTREME, "orderId=2147483647",
                () -> ApiTestSupport.postJson(mvc, "/wx/order/cancel", uid, "{\"orderId\":2147483647}"));
    }

    // ===================== 地址 =====================

    @Test
    @DisplayName("address/detail-注入与极端值")
    void addressDetail_mixed() throws Exception {
        when(addressService.query(anyInt(), anyInt())).thenReturn(null);
        probe("/wx/address/detail", T_SECURITY, "id=1 OR 1=1",
                () -> ApiTestSupport.getApi(addressMvc, "/wx/address/detail", uid, "id=1 OR 1=1"));
        probe("/wx/address/detail", T_SECURITY, "id=1;DROP TABLE",
                () -> ApiTestSupport.getApi(addressMvc, "/wx/address/detail", uid, "id=1;DROP%20TABLE%20litemall_address"));
        probe("/wx/address/detail", T_EXTREME, "id=2147483647",
                () -> ApiTestSupport.getApi(addressMvc, "/wx/address/detail", uid, "id=2147483647"));
        probe("/wx/address/detail", T_TYPE, "id=abc",
                () -> ApiTestSupport.getApi(addressMvc, "/wx/address/detail", uid, "id=abc"));
    }

    @Test
    @DisplayName("address/save-编码异常")
    void addressSave_encoding() throws Exception {
        when(addressService.query(anyInt(), anyInt())).thenReturn(ApiTestSupport.address(1, uid));
        when(addressService.add(any(LitemallAddress.class))).thenAnswer(inv -> {
            LitemallAddress a = inv.getArgument(0);
            a.setId(9);
            return null;
        });

        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longName.append('A');
        }

        probe("/wx/address/save", T_ENCODING, "name长度10000",
                () -> ApiTestSupport.postJson(addressMvc, "/wx/address/save", uid,
                        "{\"name\":\"" + longName + "\",\"tel\":\"13800000000\",\"province\":\"江西省\","
                                + "\"city\":\"南昌市\",\"county\":\"新建区\",\"areaCode\":\"360112\","
                                + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}"));

        probe("/wx/address/save", T_ENCODING, "name含emoji",
                () -> ApiTestSupport.postJson(addressMvc, "/wx/address/save", uid,
                        "{\"name\":\"徐宇菲\ud83d\ude00\",\"tel\":\"13800000000\",\"province\":\"江西省\","
                                + "\"city\":\"南昌市\",\"county\":\"新建区\",\"areaCode\":\"360112\","
                                + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}"));

        probe("/wx/address/save", T_SECURITY, "tel含SQL注入串",
                () -> ApiTestSupport.postJson(addressMvc, "/wx/address/save", uid,
                        "{\"name\":\"徐宇菲\",\"tel\":\"13800000000' OR '1'='1\",\"province\":\"江西省\","
                                + "\"city\":\"南昌市\",\"county\":\"新建区\",\"areaCode\":\"360112\","
                                + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}"));

        probe("/wx/address/save", T_ENCODING, "addressDetail含NUL字符",
                () -> ApiTestSupport.postJson(addressMvc, "/wx/address/save", uid,
                        "{\"name\":\"徐宇菲\",\"tel\":\"13800000000\",\"province\":\"江西省\","
                                + "\"city\":\"南昌市\",\"county\":\"新建区\",\"areaCode\":\"360112\","
                                + "\"addressDetail\":\"南昌航空大学\u0000前湖校区\",\"isDefault\":true}"));

        probe("/wx/address/save", T_TYPE, "isDefault=\"true\"(字符串)",
                () -> ApiTestSupport.postJson(addressMvc, "/wx/address/save", uid,
                        "{\"name\":\"徐宇菲\",\"tel\":\"13800000000\",\"province\":\"江西省\","
                                + "\"city\":\"南昌市\",\"county\":\"新建区\",\"areaCode\":\"360112\","
                                + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":\"true\"}"));
    }

    // ===================== 结果落盘 =====================

    @AfterAll
    static void dump() throws Exception {
        File moduleDir = new File("litemall-wx-api").exists() ? new File("litemall-wx-api") : new File(".");
        File out = new File(moduleDir, "target/fuzz-matrix.csv");
        out.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.println("endpoint,category,payload,httpStatus,errno,verdict");
            for (String[] row : ROWS) {
                pw.println(String.join(",", row));
            }
        }
        System.out.println("FUZZ_ROWS=" + ROWS.size() + " -> " + out.getAbsolutePath());
    }
}
