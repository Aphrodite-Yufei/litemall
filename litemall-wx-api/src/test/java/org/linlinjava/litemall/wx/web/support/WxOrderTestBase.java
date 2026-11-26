package org.linlinjava.litemall.wx.web.support;

import com.github.binarywang.wxpay.service.WxPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.core.express.ExpressService;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.wx.service.WxOrderService;
import org.linlinjava.litemall.wx.web.WxOrderController;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 订单接口测试基类：真实 WxOrderService + 下游依赖全部 mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class WxOrderTestBase {

    @Mock protected LitemallUserService userService;
    @Mock protected LitemallOrderService orderService;
    @Mock protected LitemallOrderGoodsService orderGoodsService;
    @Mock protected LitemallAddressService addressService;
    @Mock protected LitemallCartService cartService;
    @Mock protected LitemallRegionService regionService;
    @Mock protected LitemallGoodsProductService productService;
    @Mock protected WxPayService wxPayService;
    @Mock protected NotifyService notifyService;
    @Mock protected LitemallGrouponRulesService grouponRulesService;
    @Mock protected LitemallGrouponService grouponService;
    @Mock protected QCodeService qCodeService;
    @Mock protected ExpressService expressService;
    @Mock protected LitemallCommentService commentService;
    @Mock protected LitemallCouponService couponService;
    @Mock protected LitemallCouponUserService couponUserService;
    @Mock protected CouponVerifyService couponVerifyService;
    @Mock protected TaskService taskService;
    @Mock protected LitemallAftersaleService aftersaleService;

    @InjectMocks protected WxOrderService orderServiceUnderTest;

    protected MockMvc mvc;
    protected Integer uid = 1;

    @BeforeEach
    void setUpBase() {
        ApiTestSupport.initSystemConfig();
        WxOrderController controller = new WxOrderController();
        ReflectionTestUtils.setField(controller, "wxOrderService", orderServiceUnderTest);
        mvc = ApiTestSupport.mockMvc(controller);
    }
}
