package org.linlinjava.litemall.wx.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.db.domain.LitemallAddress;
import org.linlinjava.litemall.db.service.LitemallAddressService;
import org.linlinjava.litemall.db.service.LitemallRegionService;
import org.linlinjava.litemall.wx.web.support.ApiTestSupport;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 收货地址模块 4 个接口的契约测试（实验三）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WxAddressContractTest {

    private static final Integer UID = 1;

    private static final String FULL_BODY = "{\"name\":\"徐宇菲\",\"tel\":\"13800000000\","
            + "\"province\":\"江西省\",\"city\":\"南昌市\",\"county\":\"新建区\","
            + "\"areaCode\":\"360112\",\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}";

    @Mock
    private LitemallAddressService addressService;
    @Mock
    private LitemallRegionService regionService;

    @InjectMocks
    private WxAddressController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mockMvc(controller);
    }

    private LitemallAddress full(Integer id, Integer userId) {
        LitemallAddress address = ApiTestSupport.address(id, userId);
        address.setAreaCode("360112");
        address.setIsDefault(true);
        return address;
    }

    // ===================== GET /wx/address/list =====================

    @Test
    @DisplayName("list-未登录返回501")
    void list_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/address/list", null, null), 501);
    }

    @Test
    @DisplayName("list-正常返回okList分页结构")
    void list_ok() throws Exception {
        when(addressService.queryByUid(UID)).thenReturn(Arrays.asList(full(1, UID), full(2, UID)));

        ApiTestSupport.getApi(mvc, "/wx/address/list", UID, null)
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].name").value("徐宇菲"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.limit").value(2))
                .andExpect(jsonPath("$.data.pages").isNumber());
    }

    // ===================== GET /wx/address/detail =====================

    @Test
    @DisplayName("detail-未登录返回501")
    void detail_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/address/detail", null, "id=1"), 501);
    }

    @Test
    @DisplayName("detail-地址不存在返回402")
    void detail_notFound() throws Exception {
        when(addressService.query(UID, 99)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/address/detail", UID, "id=99"), 402);
    }

    @Test
    @DisplayName("detail-越权查询他人地址返回402")
    void detail_otherUser() throws Exception {
        // addressService.query(userId,id) 带归属校验，他人地址返回 null
        when(addressService.query(UID, 888)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.getApi(mvc, "/wx/address/detail", UID, "id=888"), 402);
    }

    @Test
    @DisplayName("detail-正常返回地址详情")
    void detail_ok() throws Exception {
        when(addressService.query(UID, 1)).thenReturn(full(1, UID));

        ApiTestSupport.getApi(mvc, "/wx/address/detail", UID, "id=1")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("徐宇菲"))
                .andExpect(jsonPath("$.data.tel").value("13800000000"))
                .andExpect(jsonPath("$.data.areaCode").value("360112"));
    }

    // ===================== POST /wx/address/save =====================

    @Test
    @DisplayName("save-未登录返回501")
    void save_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/save", null, FULL_BODY), 501);
    }

    @Test
    @DisplayName("save-缺少收货人姓名返回401")
    void save_missingName() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/save", UID,
                "{\"tel\":\"13800000000\",\"province\":\"江西省\",\"city\":\"南昌市\","
                        + "\"county\":\"新建区\",\"areaCode\":\"360112\","
                        + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}"), 401);
    }

    @Test
    @DisplayName("save-手机号格式非法返回401")
    void save_badTel() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/save", UID,
                "{\"name\":\"徐宇菲\",\"tel\":\"123\",\"province\":\"江西省\",\"city\":\"南昌市\","
                        + "\"county\":\"新建区\",\"areaCode\":\"360112\","
                        + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}"), 401);
    }

    @Test
    @DisplayName("save-缺少isDefault返回401")
    void save_missingDefault() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/save", UID,
                "{\"name\":\"徐宇菲\",\"tel\":\"13800000000\",\"province\":\"江西省\",\"city\":\"南昌市\","
                        + "\"county\":\"新建区\",\"areaCode\":\"360112\","
                        + "\"addressDetail\":\"南昌航空大学前湖校区\"}"), 401);
    }

    @Test
    @DisplayName("save-新增地址成功返回地址ID")
    void save_add() throws Exception {
        doAnswer(inv -> {
            ((LitemallAddress) inv.getArgument(0)).setId(9);
            return null;
        }).when(addressService).add(any(LitemallAddress.class));

        ApiTestSupport.postJson(mvc, "/wx/address/save", UID, FULL_BODY)
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data").value(9));
    }

    @Test
    @DisplayName("save-更新不存在的地址返回402")
    void save_updateNotFound() throws Exception {
        when(addressService.query(UID, 777)).thenReturn(null);

        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/save", UID,
                "{\"id\":777,\"name\":\"徐宇菲\",\"tel\":\"13800000000\",\"province\":\"江西省\","
                        + "\"city\":\"南昌市\",\"county\":\"新建区\",\"areaCode\":\"360112\","
                        + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}"), 402);
    }

    @Test
    @DisplayName("save-更新成功返回地址ID")
    void save_updateOk() throws Exception {
        when(addressService.query(UID, 5)).thenReturn(full(5, UID));

        ApiTestSupport.postJson(mvc, "/wx/address/save", UID,
                        "{\"id\":5,\"name\":\"徐宇菲\",\"tel\":\"13800000000\",\"province\":\"江西省\","
                                + "\"city\":\"南昌市\",\"county\":\"新建区\",\"areaCode\":\"360112\","
                                + "\"addressDetail\":\"南昌航空大学前湖校区\",\"isDefault\":true}")
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data").value(5));
    }

    // ===================== POST /wx/address/delete =====================

    @Test
    @DisplayName("delete-未登录返回501")
    void delete_unlogin() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/delete", null,
                "{\"id\":1}"), 501);
    }

    @Test
    @DisplayName("delete-缺少地址ID返回401")
    void delete_missingId() throws Exception {
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/delete", UID, "{}"), 401);
    }

    @Test
    @DisplayName("delete-删除他人地址返回402")
    void delete_otherUser() throws Exception {
        when(addressService.query(UID, 888)).thenReturn(null);
        ApiTestSupport.assertErrno(ApiTestSupport.postJson(mvc, "/wx/address/delete", UID,
                "{\"id\":888}"), 402);
    }

    @Test
    @DisplayName("delete-成功返回无data的成功响应")
    void delete_ok() throws Exception {
        when(addressService.query(UID, 1)).thenReturn(full(1, UID));

        ApiTestSupport.assertOk(ApiTestSupport.postJson(mvc, "/wx/address/delete", UID, "{\"id\":1}"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
