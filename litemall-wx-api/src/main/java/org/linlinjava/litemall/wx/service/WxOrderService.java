package org.linlinjava.litemall.wx.service;

import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.order.WxPayMwebOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.result.BaseWxPayResult;
import com.github.binarywang.wxpay.constant.WxPayConstants;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.express.ExpressService;
import org.linlinjava.litemall.core.express.dao.ExpressInfo;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.notify.NotifyType;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.core.util.DateTimeUtil;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.*;
import org.linlinjava.litemall.db.util.CouponUserConstant;
import org.linlinjava.litemall.db.util.GrouponConstant;
import org.linlinjava.litemall.db.util.OrderHandleOption;
import org.linlinjava.litemall.db.util.OrderUtil;
import org.linlinjava.litemall.core.util.IpUtil;
import org.linlinjava.litemall.wx.task.OrderUnpaidTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.linlinjava.litemall.wx.util.WxResponseCode.*;

/**
 * 订单服务
 *
 * <p>
 * 订单状态：
 * 101 订单生成，未支付；102，下单后未支付用户取消；103，下单后未支付超时系统自动取消
 * 201 支付完成，商家未发货；202，订单生产，已付款未发货，但是退款取消；
 * 301 商家发货，用户未确认；
 * 401 用户确认收货； 402 用户没有确认收货超过一定时间，系统自动确认收货；
 *
 * <p>
 * 用户操作：
 * 当101用户未付款时，此时用户可以进行的操作是取消订单，或者付款操作
 * 当201支付完成而商家未发货时，此时用户可以取消订单并申请退款
 * 当301商家已发货时，此时用户可以有确认收货的操作
 * 当401用户确认收货以后，此时用户可以进行的操作是删除订单，评价商品，申请售后，或者再次购买
 * 当402系统自动确认收货以后，此时用户可以删除订单，评价商品，申请售后，或者再次购买
 */
@Service
public class WxOrderService {
    private final Log logger = LogFactory.getLog(WxOrderService.class);

    @Autowired
    private LitemallUserService userService;
    @Autowired
    private LitemallOrderService orderService;
    @Autowired
    private LitemallOrderGoodsService orderGoodsService;
    @Autowired
    private LitemallAddressService addressService;
    @Autowired
    private LitemallCartService cartService;
    @Autowired
    private LitemallRegionService regionService;
    @Autowired
    private LitemallGoodsProductService productService;
    @Autowired
    private WxPayService wxPayService;
    @Autowired
    private NotifyService notifyService;
    @Autowired
    private LitemallGrouponRulesService grouponRulesService;
    @Autowired
    private LitemallGrouponService grouponService;
    @Autowired
    private QCodeService qCodeService;
    @Autowired
    private ExpressService expressService;
    @Autowired
    private LitemallCommentService commentService;
    @Autowired
    private LitemallCouponService couponService;
    @Autowired
    private LitemallCouponUserService couponUserService;
    @Autowired
    private CouponVerifyService couponVerifyService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private LitemallAftersaleService aftersaleService;

    /**
     * 订单列表
     *
     * @param userId   用户ID
     * @param showType 订单信息：
     *                 0，全部订单；
     *                 1，待付款；
     *                 2，待发货；
     *                 3，待收货；
     *                 4，待评价。
     * @param page     分页页数
     * @param limit     分页大小
     * @return 订单列表
     */
    public Object list(Integer userId, Integer showType, Integer page, Integer limit, String sort, String order) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        List<Short> orderStatus = OrderUtil.orderStatus(showType);
        List<LitemallOrder> orderList = orderService.queryByOrderStatus(userId, orderStatus, page, limit, sort, order);

        List<Map<String, Object>> orderVoList = new ArrayList<>(orderList.size());
        for (LitemallOrder o : orderList) {
            Map<String, Object> orderVo = new HashMap<>();
            orderVo.put("id", o.getId());
            orderVo.put("orderSn", o.getOrderSn());
            orderVo.put("actualPrice", o.getActualPrice());
            orderVo.put("orderStatusText", OrderUtil.orderStatusText(o));
            orderVo.put("handleOption", OrderUtil.build(o));
            orderVo.put("aftersaleStatus", o.getAftersaleStatus());

            LitemallGroupon groupon = grouponService.queryByOrderId(o.getId());
            if (groupon != null) {
                orderVo.put("isGroupin", true);
            } else {
                orderVo.put("isGroupin", false);
            }

            List<LitemallOrderGoods> orderGoodsList = orderGoodsService.queryByOid(o.getId());
            List<Map<String, Object>> orderGoodsVoList = new ArrayList<>(orderGoodsList.size());
            for (LitemallOrderGoods orderGoods : orderGoodsList) {
                Map<String, Object> orderGoodsVo = new HashMap<>();
                orderGoodsVo.put("id", orderGoods.getId());
                orderGoodsVo.put("goodsName", orderGoods.getGoodsName());
                orderGoodsVo.put("number", orderGoods.getNumber());
                orderGoodsVo.put("picUrl", orderGoods.getPicUrl());
                orderGoodsVo.put("specifications", orderGoods.getSpecifications());
                orderGoodsVo.put("price",orderGoods.getPrice());
                orderGoodsVoList.add(orderGoodsVo);
            }
            orderVo.put("goodsList", orderGoodsVoList);

            orderVoList.add(orderVo);
        }

        return ResponseUtil.okList(orderVoList, orderList);
    }

    /**
     * 订单详情
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单详情
     */
    public Object detail(Integer userId, Integer orderId) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        // 订单信息
        LitemallOrder order = orderService.findById(userId, orderId);
        if (null == order) {
            return ResponseUtil.fail(ORDER_UNKNOWN, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.fail(ORDER_INVALID, "不是当前用户的订单");
        }
        Map<String, Object> orderVo = new HashMap<String, Object>();
        orderVo.put("id", order.getId());
        orderVo.put("orderSn", order.getOrderSn());
        orderVo.put("message", order.getMessage());
        orderVo.put("addTime", order.getAddTime());
        orderVo.put("consignee", order.getConsignee());
        orderVo.put("mobile", order.getMobile());
        orderVo.put("address", order.getAddress());
        orderVo.put("goodsPrice", order.getGoodsPrice());
        orderVo.put("couponPrice", order.getCouponPrice());
        orderVo.put("freightPrice", order.getFreightPrice());
        orderVo.put("actualPrice", order.getActualPrice());
        orderVo.put("orderStatusText", OrderUtil.orderStatusText(order));
        orderVo.put("handleOption", OrderUtil.build(order));
        orderVo.put("aftersaleStatus", order.getAftersaleStatus());
        orderVo.put("expCode", order.getShipChannel());
        orderVo.put("expName", expressService.getVendorName(order.getShipChannel()));
        orderVo.put("expNo", order.getShipSn());

        List<LitemallOrderGoods> orderGoodsList = orderGoodsService.queryByOid(order.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("orderInfo", orderVo);
        result.put("orderGoods", orderGoodsList);

        // 订单状态为已发货且物流信息不为空
        //"YTO", "800669400640887922"
        if (order.getOrderStatus().equals(OrderUtil.STATUS_SHIP)) {
            ExpressInfo ei = expressService.getExpressInfo(order.getShipChannel(), order.getShipSn());
            if(ei == null){
                result.put("expressInfo", new ArrayList<>());
            }
            else {
                result.put("expressInfo", ei);
            }
        }
        else{
            result.put("expressInfo", new ArrayList<>());
        }

        return ResponseUtil.ok(result);

    }

    /**
     * 提交订单
     * <p>
     * 1. 创建订单表项和订单商品表项;
     * 2. 购物车清空;
     * 3. 优惠券设置已用;
     * 4. 商品货品库存减少;
     * 5. 如果是团购商品，则创建团购活动表项。
     *
     * @param userId 用户ID
     * @param body   订单信息，{ cartId：xxx, addressId: xxx, couponId: xxx, message: xxx, grouponRulesId: xxx,  grouponLinkId: xxx}
     * @return 提交订单操作结果
     */
    @Transactional
    public Object submit(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        if (body == null) {
            return ResponseUtil.badArgument();
        }

        SubmitParams params = SubmitParams.parse(body);
        if (!params.isValid()) {
            return ResponseUtil.badArgument();
        }

        // 团购规则校验（只查询一次规则，避免重复 DB 访问）
        Object grouponError = validateGrouponRules(params.grouponRulesId, params.grouponLinkId, userId);
        if (grouponError != null) {
            return grouponError;
        }

        LitemallAddress checkedAddress = addressService.query(userId, params.addressId);
        if (checkedAddress == null) {
            return ResponseUtil.badArgument();
        }

        // 团购优惠：规则不存在或折扣为空时按原价兜底，避免 NPE
        LitemallGrouponRules grouponRules = grouponRulesService.findById(params.grouponRulesId);
        BigDecimal grouponPrice = grouponRules == null ? BigDecimal.ZERO
                : (grouponRules.getDiscount() == null ? BigDecimal.ZERO : grouponRules.getDiscount());

        List<LitemallCart> checkedGoodsList = resolveCheckedGoodsList(params.cartId, userId);
        if (checkedGoodsList == null) {
            // 实验三修复（IDOR）：cartId 对应的购物车记录不属于当前用户，越权下单
            return ResponseUtil.unauthz();
        }
        if (checkedGoodsList.isEmpty()) {
            return ResponseUtil.badArgumentValue();
        }
        if (hasInvalidGoodsNumber(checkedGoodsList)) {
            // 实验三修复：购物车数量必须为正整数，负数数量会算出负金额并造成 0 元订单、库存反向增加
            return ResponseUtil.badArgumentValue();
        }
        if (hasProductProblem(checkedGoodsList)) {
            // 实验三修复（未捕获异常）：货品已删除或库存不足时在下单前拦截，
            // 避免 deductStock 抛 RuntimeException 被 GlobalExceptionHandler 兜成 errno=502
            return ResponseUtil.badArgumentValue();
        }

        BigDecimal checkedGoodsPrice = calcCheckedGoodsPrice(checkedGoodsList, grouponRules, grouponPrice);
        BigDecimal couponPrice = resolveCouponPrice(userId, params.couponId, params.userCouponId,
                checkedGoodsPrice, checkedGoodsList);
        if (couponPrice == null) {
            return ResponseUtil.badArgumentValue();
        }
        BigDecimal freightPrice = checkedGoodsPrice.compareTo(SystemConfig.getFreightLimit()) < 0
                ? SystemConfig.getFreight() : BigDecimal.ZERO;
        BigDecimal actualPrice = checkedGoodsPrice.add(freightPrice).subtract(couponPrice).max(BigDecimal.ZERO);

        // 实验四修复（并发超卖）：库存扣减前置到订单落库之前。
        // 改造前先 add 订单、后扣库存，扣减失败抛出的 RuntimeException 被 catch 吞掉，
        // 方法以正常返回结束，Spring 事务照常提交 —— 订单入库、库存未扣，200 并发抢
        // 50 件库存时订单数会显著大于 50（超卖）。前置后扣减失败时订单尚未写入，
        // 并显式 setRollbackOnly 兜底，保证「要么都成功、要么都回滚」。
        try {
            if (!deductStock(checkedGoodsList)) {
                // 快速失败路径：未写入任何数据，直接返回 505，不需要回滚，耗时更短
                return ResponseUtil.updatedDataFailed();
            }
        } catch (RuntimeException e) {
            // 慢路径：多商品扣减中途失败或并发竞态，必须回滚已扣的库存
            logger.error("订单库存扣减失败 userId=" + userId, e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseUtil.updatedDataFailed();
        }

        LitemallOrder order = buildOrder(userId, params, checkedAddress, grouponRules, grouponPrice,
                checkedGoodsPrice, couponPrice, freightPrice, actualPrice);
        orderService.add(order);
        Integer orderId = order.getId();

        addOrderGoods(order, checkedGoodsList);
        clearCart(params.cartId, userId);
        useCoupon(params.couponId, params.userCouponId, orderId);
        Integer grouponLinkId = createGrouponRecord(params.grouponRulesId, params.grouponLinkId, userId, orderId);

        boolean payed = false;
        if (actualPrice.compareTo(BigDecimal.ZERO) == 0) {
            payed = true;
            handleZeroPayOrder(orderId);
        } else {
            // 订单支付超期任务
            taskService.addTask(new OrderUnpaidTask(orderId));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("payed", payed);
        data.put("grouponLinkId", grouponLinkId);
        return ResponseUtil.ok(data);
    }

    /**
     * 订单提交参数封装（人工重构：将 body 字符串解析集中到一处）
     */
    private static class SubmitParams {
        final Integer cartId;
        final Integer addressId;
        final Integer couponId;
        final Integer userCouponId;
        final String message;
        final Integer grouponRulesId;
        final Integer grouponLinkId;

        private SubmitParams(Integer cartId, Integer addressId, Integer couponId, Integer userCouponId,
                             String message, Integer grouponRulesId, Integer grouponLinkId) {
            this.cartId = cartId;
            this.addressId = addressId;
            this.couponId = couponId;
            this.userCouponId = userCouponId;
            this.message = message;
            this.grouponRulesId = grouponRulesId;
            this.grouponLinkId = grouponLinkId;
        }

        static SubmitParams parse(String body) {
            return new SubmitParams(
                    JacksonUtil.parseInteger(body, "cartId"),
                    JacksonUtil.parseInteger(body, "addressId"),
                    JacksonUtil.parseInteger(body, "couponId"),
                    JacksonUtil.parseInteger(body, "userCouponId"),
                    JacksonUtil.parseString(body, "message"),
                    JacksonUtil.parseInteger(body, "grouponRulesId"),
                    JacksonUtil.parseInteger(body, "grouponLinkId"));
        }

        boolean isValid() {
            return cartId != null && addressId != null && couponId != null;
        }
    }

    /**
     * 团购规则校验（人工重构：提取为独立方法，消除原方法中 4 层嵌套 if 分支）
     * 返回 null 表示校验通过；否则返回错误响应，由 submit 直接返回给前端。
     */
    private Object validateGrouponRules(Integer grouponRulesId, Integer grouponLinkId, Integer userId) {
        if (grouponRulesId == null || grouponRulesId <= 0) {
            return null;
        }
        LitemallGrouponRules rules = grouponRulesService.findById(grouponRulesId);
        // 找不到记录
        if (rules == null) {
            return ResponseUtil.badArgument();
        }
        // 团购规则已经过期
        if (GrouponConstant.RULE_STATUS_DOWN_EXPIRE.equals(rules.getStatus())) {
            return ResponseUtil.fail(GROUPON_EXPIRED, "团购已过期!");
        }
        // 团购规则已经下线
        if (GrouponConstant.RULE_STATUS_DOWN_ADMIN.equals(rules.getStatus())) {
            return ResponseUtil.fail(GROUPON_OFFLINE, "团购已下线!");
        }
        if (grouponLinkId == null || grouponLinkId <= 0) {
            return null;
        }
        // 团购人数已满
        if (grouponService.countGroupon(grouponLinkId) >= (rules.getDiscountMember() - 1)) {
            return ResponseUtil.fail(GROUPON_FULL, "团购活动人数已满!");
        }
        // 不允许参加已经加入的团购
        if (grouponService.hasJoin(userId, grouponLinkId)) {
            return ResponseUtil.fail(GROUPON_JOIN, "团购活动已经参加!");
        }
        // 不允许参加自己开团的团购
        LitemallGroupon groupon = grouponService.queryById(userId, grouponLinkId);
        if (groupon != null && groupon.getCreatorUserId().equals(userId)) {
            return ResponseUtil.fail(GROUPON_JOIN, "团购活动已经参加!");
        }
        return null;
    }

    /**
     * 解析选中商品（cartId=0 表示购物车全部选中商品；人工重构：增加 cart 为空判空，修复潜在 NPE）
     * <p>
     * 实验三修复（IDOR）：cartId 非 0 时必须校验购物车记录归属当前用户，否则返回 null 表示越权。
     */
    private List<LitemallCart> resolveCheckedGoodsList(Integer cartId, Integer userId) {
        if (cartId.equals(0)) {
            return cartService.queryByUidAndChecked(userId);
        }
        LitemallCart cart = cartService.findById(cartId);
        if (cart == null) {
            return new ArrayList<>();
        }
        if (!userId.equals(cart.getUserId())) {
            return null;
        }
        List<LitemallCart> list = new ArrayList<>(1);
        list.add(cart);
        return list;
    }

    /**
     * 实验三新增：校验购物车商品数量合法（非 null 且为正），避免负数数量导致的负金额订单与库存反向增加。
     */
    private boolean hasInvalidGoodsNumber(List<LitemallCart> checkedGoodsList) {
        for (LitemallCart cart : checkedGoodsList) {
            if (cart.getNumber() == null || cart.getNumber() <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 货品有效性校验（实验三新增）。
     * <p>
     * 原实现直到 deductStock 才检查货品是否存在/库存是否充足，并以 RuntimeException 抛出，
     * 被全局异常处理器兜成 errno=502，属于「未捕获异常导致 500 等效崩溃」。
     * 此处提前到建单前校验，转为语义明确的 402 参数错误。
     */
    private boolean hasProductProblem(List<LitemallCart> checkedGoodsList) {
        for (LitemallCart cart : checkedGoodsList) {
            LitemallGoodsProduct product = productService.findById(cart.getProductId());
            if (product == null) {
                return true;
            }
            // 实验四修复：此处不再比对库存余量。原判断是 TOCTOU 竞态——
            // 200 个并发请求可能同时读到「余量 50」而全部放行，既挡不住超卖，
            // 又多了一次与 deductStock 重复的商品查询。库存是否充足只由
            // deductStock 中的原子条件更新（UPDATE ... WHERE number >= ?）裁定。
        }
        return false;
    }

    /**
     * 商品价格计算（人工重构：提取为私有方法；团购折扣为空时按原价兜底）
     */
    private BigDecimal calcCheckedGoodsPrice(List<LitemallCart> checkedGoodsList,
                                            LitemallGrouponRules grouponRules, BigDecimal grouponPrice) {
        BigDecimal price = BigDecimal.ZERO;
        for (LitemallCart checkGoods : checkedGoodsList) {
            // 只有当团购规格商品ID符合才进行团购优惠
            if (grouponRules != null && grouponRules.getGoodsId().equals(checkGoods.getGoodsId())) {
                price = price.add(checkGoods.getPrice().subtract(grouponPrice).multiply(new BigDecimal(checkGoods.getNumber())));
            } else {
                price = price.add(checkGoods.getPrice().multiply(new BigDecimal(checkGoods.getNumber())));
            }
        }
        return price;
    }

    /**
     * 优惠券金额计算（couponId=0 表示没有优惠券，couponId=-1 表示不使用优惠券；
     * 人工重构：提取为独立方法；返回 null 表示优惠券不可用）
     */
    private BigDecimal resolveCouponPrice(Integer userId, Integer couponId, Integer userCouponId,
                                          BigDecimal checkedGoodsPrice, List<LitemallCart> checkedGoodsList) {
        if (couponId == 0 || couponId == -1) {
            return BigDecimal.ZERO;
        }
        LitemallCoupon coupon = couponVerifyService.checkCoupon(userId, couponId, userCouponId,
                checkedGoodsPrice, checkedGoodsList);
        if (coupon == null) {
            return null;
        }
        return coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
    }

    /**
     * 订单构建（人工重构：提取为独立方法，将订单字段赋值集中管理）
     */
    private LitemallOrder buildOrder(Integer userId, SubmitParams params, LitemallAddress checkedAddress,
                                     LitemallGrouponRules grouponRules, BigDecimal grouponPrice,
                                     BigDecimal checkedGoodsPrice, BigDecimal couponPrice,
                                     BigDecimal freightPrice, BigDecimal actualPrice) {
        LitemallOrder order = new LitemallOrder();
        order.setUserId(userId);
        order.setOrderSn(orderService.generateOrderSn(userId));
        order.setOrderStatus(OrderUtil.STATUS_CREATE);
        order.setConsignee(checkedAddress.getName());
        order.setMobile(checkedAddress.getTel());
        order.setMessage(params.message);
        String detailedAddress = checkedAddress.getProvince() + checkedAddress.getCity()
                + checkedAddress.getCounty() + " " + checkedAddress.getAddressDetail();
        order.setAddress(detailedAddress);
        order.setGoodsPrice(checkedGoodsPrice);
        order.setFreightPrice(freightPrice);
        order.setCouponPrice(couponPrice);
        order.setIntegralPrice(BigDecimal.ZERO);
        order.setOrderPrice(checkedGoodsPrice.add(freightPrice).subtract(couponPrice).max(BigDecimal.ZERO));
        order.setActualPrice(actualPrice);
        // 有团购则记录团购价格，否则记为 0
        order.setGrouponPrice(grouponRules == null ? BigDecimal.ZERO : grouponPrice);
        return order;
    }

    /**
     * 添加订单商品表项（人工重构：提取为私有方法）
     */
    private void addOrderGoods(LitemallOrder order, List<LitemallCart> checkedGoodsList) {
        for (LitemallCart cartGoods : checkedGoodsList) {
            LitemallOrderGoods orderGoods = new LitemallOrderGoods();
            orderGoods.setOrderId(order.getId());
            orderGoods.setGoodsId(cartGoods.getGoodsId());
            orderGoods.setGoodsSn(cartGoods.getGoodsSn());
            orderGoods.setProductId(cartGoods.getProductId());
            orderGoods.setGoodsName(cartGoods.getGoodsName());
            orderGoods.setPicUrl(cartGoods.getPicUrl());
            orderGoods.setPrice(cartGoods.getPrice());
            orderGoods.setNumber(cartGoods.getNumber());
            orderGoods.setSpecifications(cartGoods.getSpecifications());
            orderGoods.setAddTime(LocalDateTime.now());
            orderGoodsService.add(orderGoods);
        }
    }

    /**
     * 清空购物车（人工重构：提取为私有方法）
     */
    private void clearCart(Integer cartId, Integer userId) {
        if (cartId.equals(0)) {
            cartService.clearGoods(userId);
        } else {
            cartService.deleteById(cartId);
        }
    }

    /**
     * 库存扣减（人工重构：增加 product 判空修复潜在 NPE；
     * 超卖防护依赖 DAO 层原子条件更新 reduceStock，本方法校验后触发）
     */
    /**
     * 库存扣减。实验四修复要点：
     * 1) 删除「先查 number 再减」的内存预判断——并发下两个线程会同时通过判断，
     *    权威判定必须交给 reduceStock 的原子条件更新（UPDATE ... WHERE number >= ?）；
     * 2) 由调用方保证在订单落库之前执行，并配合事务回滚，杜绝「订单有、库存无」；
     * 3) 库存明显不足时走快速失败（返回 false），不抛异常、不进入回滚慢路径，
     *    只有多商品扣减中途失败这种真正需要撤销已扣项的竞态才抛异常，保证吞吐。
     *
     * @return true 扣减成功；false 库存不足且尚未写入任何扣减
     */
    private boolean deductStock(List<LitemallCart> checkedGoodsList) {
        boolean anyDeducted = false;
        for (LitemallCart checkGoods : checkedGoodsList) {
            // 实验四性能优化：不再先 findById 查一次库存再判断。该写法既多一次数据库
            // 往返（每单 +1 次 SELECT），又解决不了 TOCTOU 竞态。直接以原子条件更新的
            // 返回值作为唯一权威判定：返回 0 即「库存不足或货品不存在」。
            if (productService.reduceStock(checkGoods.getProductId(), checkGoods.getNumber()) == 0) {
                if (!anyDeducted) {
                    // 一件都没扣成功，快速失败，无需回滚
                    return false;
                }
                // 多商品场景已扣了前面若干件，必须抛异常触发事务回滚
                throw new RuntimeException("商品货品库存减少失败：库存不足或货品不存在");
            }
            anyDeducted = true;
        }
        return true;
    }

    /**
     * 优惠券核销（人工重构：修复 couponUser 未判空导致的 NPE——AI 重构方案未覆盖此点，由人工审计补充）
     */
    private void useCoupon(Integer couponId, Integer userCouponId, Integer orderId) {
        if (couponId == 0 || couponId == -1 || userCouponId == null) {
            return;
        }
        LitemallCouponUser couponUser = couponUserService.findById(userCouponId);
        if (couponUser == null) {
            throw new RuntimeException("优惠券用户记录不存在");
        }
        couponUser.setStatus(CouponUserConstant.STATUS_USED);
        couponUser.setUsedTime(LocalDateTime.now());
        couponUser.setOrderId(orderId);
        couponUserService.update(couponUser);
    }

    /**
     * 团购记录创建（人工重构：保留原业务中团购开团/参团记录创建逻辑，AI 重构方案遗漏，由人工审计补回）
     * 返回最终 grouponLinkId，供前端跳转团购详情使用；无团购时返回 null。
     */
    private Integer createGrouponRecord(Integer grouponRulesId, Integer grouponLinkId, Integer userId, Integer orderId) {
        if (grouponRulesId == null || grouponRulesId <= 0) {
            return 0;
        }
        LitemallGroupon groupon = new LitemallGroupon();
        groupon.setOrderId(orderId);
        groupon.setStatus(GrouponConstant.STATUS_NONE);
        groupon.setUserId(userId);
        groupon.setRulesId(grouponRulesId);

        if (grouponLinkId != null && grouponLinkId > 0) {
            // 参与者：关联已经开好的团
            LitemallGroupon baseGroupon = grouponService.queryById(grouponLinkId);
            groupon.setCreatorUserId(baseGroupon.getCreatorUserId());
            groupon.setGrouponId(grouponLinkId);
            groupon.setShareUrl(baseGroupon.getShareUrl());
            grouponService.createGroupon(groupon);
        } else {
            // 开团者：创建新的团购记录
            groupon.setCreatorUserId(userId);
            groupon.setCreatorUserTime(LocalDateTime.now());
            groupon.setGrouponId(0);
            grouponService.createGroupon(groupon);
            grouponLinkId = groupon.getId();
        }
        return grouponLinkId;
    }

    /**
     * 0 元订单处理（人工重构：BigDecimal.equals("0.00") 改为 compareTo(BigDecimal.ZERO)，
     * 修复金额精度不一致导致 0 元判断失效的缺陷；支付成功后的团购状态更新与通知逻辑一并拆出）
     */
    private void handleZeroPayOrder(Integer orderId) {
        LitemallOrder o = new LitemallOrder();
        o.setId(orderId);
        o.setOrderStatus(OrderUtil.STATUS_PAY);
        orderService.updateSelective(o);

        // 支付成功，有团购信息则更新团购状态
        LitemallGroupon groupon = grouponService.queryByOrderId(orderId);
        if (groupon != null) {
            LitemallGrouponRules grouponRules = grouponRulesService.findById(groupon.getRulesId());
            // 仅当发起者才创建分享图片
            if (groupon.getGrouponId() == 0) {
                String url = qCodeService.createGrouponShareImage(grouponRules.getGoodsName(),
                        grouponRules.getPicUrl(), groupon);
                groupon.setShareUrl(url);
            }
            groupon.setStatus(GrouponConstant.STATUS_ON);
            if (grouponService.updateById(groupon) == 0) {
                throw new RuntimeException("更新数据已失效");
            }

            // 参团者且人数达到成团条件时，将相关团购记录置为成功
            if (groupon.getGrouponId() != 0) {
                List<LitemallGroupon> grouponList = grouponService.queryJoinRecord(groupon.getGrouponId());
                if (grouponList.size() >= grouponRules.getDiscountMember() - 1) {
                    for (LitemallGroupon grouponActivity : grouponList) {
                        grouponActivity.setStatus(GrouponConstant.STATUS_SUCCEED);
                        grouponService.updateById(grouponActivity);
                    }
                    LitemallGroupon grouponSource = grouponService.queryById(groupon.getGrouponId());
                    grouponSource.setStatus(GrouponConstant.STATUS_SUCCEED);
                    grouponService.updateById(grouponSource);
                }
            }
        }

        // 异步发送邮件与短信通知；微信短信平台参数长度有限，订单号取 8-14 位，长度不足时整串发送（修复下标越界）
        LitemallOrder order = orderService.findById(orderId);
        if (order == null) {
            // 实验三修复：订单读取失败时直接返回，避免 order.getOrderSn() 抛出 NPE（502 崩溃）
            return;
        }
        notifyService.notifyMail("新订单通知", order.toString());
        String orderSn = order.getOrderSn();
        String smsOrderNo = orderSn.length() >= 14 ? orderSn.substring(8, 14) : orderSn;
        notifyService.notifySmsTemplateSync(order.getMobile(), NotifyType.PAY_SUCCEED, new String[]{smsOrderNo});
    }


    /**
     * 取消订单
     * <p>
     * 1. 检测当前订单是否能够取消；
     * 2. 设置订单取消状态；
     * 3. 商品货品库存恢复；
     * 4. 返还优惠券；
     *
     * @param userId 用户ID
     * @param body   订单信息，{ orderId：xxx }
     * @return 取消订单操作结果
     */
    @Transactional
    public Object cancel(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        LocalDateTime preUpdateTime = order.getUpdateTime();

        // 检测是否能够取消
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isCancel()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "订单不能取消");
        }

        // 设置订单已取消状态
        order.setOrderStatus(OrderUtil.STATUS_CANCEL);
        order.setEndTime(LocalDateTime.now());
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            throw new RuntimeException("更新数据已失效");
        }

        // 商品货品数量增加
        List<LitemallOrderGoods> orderGoodsList = orderGoodsService.queryByOid(orderId);
        for (LitemallOrderGoods orderGoods : orderGoodsList) {
            Integer productId = orderGoods.getProductId();
            Short number = orderGoods.getNumber();
            if (productService.addStock(productId, number) == 0) {
                throw new RuntimeException("商品货品库存增加失败");
            }
        }

        // 返还优惠券
        releaseCoupon(orderId);

        return ResponseUtil.ok();
    }

    /**
     * 付款订单的预支付会话标识
     * <p>
     * 1. 检测当前订单是否能够付款
     * 2. 微信商户平台返回支付订单ID
     * 3. 设置订单付款状态
     *
     * @param userId 用户ID
     * @param body   订单信息，{ orderId：xxx }
     * @return 支付订单ID
     */
    @Transactional
    public Object prepay(Integer userId, String body, HttpServletRequest request) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        // 检测是否能够取消
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isPay()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "订单不能支付");
        }

        LitemallUser user = userService.findById(userId);
        String openid = user.getWeixinOpenid();
        if (openid == null) {
            return ResponseUtil.fail(AUTH_OPENID_UNACCESS, "订单不能支付");
        }
        WxPayMpOrderResult result = null;
        try {
            WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
            orderRequest.setOutTradeNo(order.getOrderSn());
            orderRequest.setOpenid(openid);
            orderRequest.setBody("订单：" + order.getOrderSn());
            // 元转成分
            int fee = 0;
            BigDecimal actualPrice = order.getActualPrice();
            fee = actualPrice.multiply(new BigDecimal(100)).intValue();
            orderRequest.setTotalFee(fee);
            orderRequest.setSpbillCreateIp(IpUtil.getIpAddr(request));

            result = wxPayService.createOrder(orderRequest);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseUtil.fail(ORDER_PAY_FAIL, "订单不能支付");
        }

        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return ResponseUtil.updatedDateExpired();
        }
        return ResponseUtil.ok(result);
    }

    /**
     * 微信H5支付
     *
     * @param userId
     * @param body
     * @param request
     * @return
     */
    @Transactional
    public Object h5pay(Integer userId, String body, HttpServletRequest request) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        // 检测是否能够取消
        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isPay()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "订单不能支付");
        }

        WxPayMwebOrderResult result = null;
        try {
            WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
            orderRequest.setOutTradeNo(order.getOrderSn());
            orderRequest.setTradeType("MWEB");
            orderRequest.setBody("订单：" + order.getOrderSn());
            // 元转成分
            int fee = 0;
            BigDecimal actualPrice = order.getActualPrice();
            fee = actualPrice.multiply(new BigDecimal(100)).intValue();
            orderRequest.setTotalFee(fee);
            orderRequest.setSpbillCreateIp(IpUtil.getIpAddr(request));

            result = wxPayService.createOrder(orderRequest);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseUtil.ok(result);
    }

    /**
     * 微信付款成功或失败回调接口
     * <p>
     * 1. 检测当前订单是否是付款状态;
     * 2. 设置订单付款成功状态相关信息;
     * 3. 响应微信商户平台.
     *
     * @param request  请求内容
     * @param response 响应内容
     * @return 操作结果
     */
    @Transactional
    public Object payNotify(HttpServletRequest request, HttpServletResponse response) {
        String xmlResult = null;
        try {
            xmlResult = IOUtils.toString(request.getInputStream(), request.getCharacterEncoding());
        } catch (IOException e) {
            e.printStackTrace();
            return WxPayNotifyResponse.fail(e.getMessage());
        }

        WxPayOrderNotifyResult result = null;
        try {
            result = wxPayService.parseOrderNotifyResult(xmlResult);

            if(!WxPayConstants.ResultCode.SUCCESS.equals(result.getResultCode())){
                logger.error(xmlResult);
                throw new WxPayException("微信通知支付失败！");
            }
            if(!WxPayConstants.ResultCode.SUCCESS.equals(result.getReturnCode())){
                logger.error(xmlResult);
                throw new WxPayException("微信通知支付失败！");
            }
        } catch (WxPayException e) {
            e.printStackTrace();
            return WxPayNotifyResponse.fail(e.getMessage());
        }

        logger.info("处理腾讯支付平台的订单支付");
        logger.info(result);

        String orderSn = result.getOutTradeNo();
        String payId = result.getTransactionId();

        // 分转化成元
        String totalFee = BaseWxPayResult.fenToYuan(result.getTotalFee());
        LitemallOrder order = orderService.findBySn(orderSn);
        if (order == null) {
            return WxPayNotifyResponse.fail("订单不存在 sn=" + orderSn);
        }

        // 检查这个订单是否已经处理过
        if (OrderUtil.hasPayed(order)) {
            return WxPayNotifyResponse.success("订单已经处理成功!");
        }

        // 检查支付订单金额
        if (!totalFee.equals(order.getActualPrice().toString())) {
            return WxPayNotifyResponse.fail(order.getOrderSn() + " : 支付金额不符合 totalFee=" + totalFee);
        }

        order.setPayId(payId);
        order.setPayTime(LocalDateTime.now());
        order.setOrderStatus(OrderUtil.STATUS_PAY);
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return WxPayNotifyResponse.fail("更新数据已失效");
        }

        //  支付成功，有团购信息，更新团购信息
        LitemallGroupon groupon = grouponService.queryByOrderId(order.getId());
        if (groupon != null) {
            LitemallGrouponRules grouponRules = grouponRulesService.findById(groupon.getRulesId());

            //仅当发起者才创建分享图片
            if (groupon.getGrouponId() == 0) {
                String url = qCodeService.createGrouponShareImage(grouponRules.getGoodsName(), grouponRules.getPicUrl(), groupon);
                groupon.setShareUrl(url);
            }
            groupon.setStatus(GrouponConstant.STATUS_ON);
            if (grouponService.updateById(groupon) == 0) {
                return WxPayNotifyResponse.fail("更新数据已失效");
            }


            List<LitemallGroupon> grouponList = grouponService.queryJoinRecord(groupon.getGrouponId());
            if (groupon.getGrouponId() != 0 && (grouponList.size() >= grouponRules.getDiscountMember() - 1)) {
                for (LitemallGroupon grouponActivity : grouponList) {
                    grouponActivity.setStatus(GrouponConstant.STATUS_SUCCEED);
                    grouponService.updateById(grouponActivity);
                }

                LitemallGroupon grouponSource = grouponService.queryById(groupon.getGrouponId());
                grouponSource.setStatus(GrouponConstant.STATUS_SUCCEED);
                grouponService.updateById(grouponSource);
            }
        }

        //TODO 发送邮件和短信通知，这里采用异步发送
        // 订单支付成功以后，会发送短信给用户，以及发送邮件给管理员
        notifyService.notifyMail("新订单通知", order.toString());
        // 这里微信的短信平台对参数长度有限制，所以将订单号只截取后6位
        notifyService.notifySmsTemplateSync(order.getMobile(), NotifyType.PAY_SUCCEED, new String[]{orderSn.substring(8, 14)});

        // 取消订单超时未支付任务
        taskService.removeTask(new OrderUnpaidTask(order.getId()));

        return WxPayNotifyResponse.success("处理成功!");
    }

    /**
     * 订单申请退款
     * <p>
     * 1. 检测当前订单是否能够退款；
     * 2. 设置订单申请退款状态。
     *
     * @param userId 用户ID
     * @param body   订单信息，{ orderId：xxx }
     * @return 订单退款操作结果
     */
    public Object refund(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isRefund()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "订单不能取消");
        }

        // 设置订单申请退款状态
        order.setOrderStatus(OrderUtil.STATUS_REFUND);
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return ResponseUtil.updatedDateExpired();
        }

        //TODO 发送邮件和短信通知，这里采用异步发送
        // 有用户申请退款，邮件通知运营人员
        notifyService.notifyMail("退款申请", order.toString());

        return ResponseUtil.ok();
    }

    /**
     * 确认收货
     * <p>
     * 1. 检测当前订单是否能够确认收货；
     * 2. 设置订单确认收货状态。
     *
     * @param userId 用户ID
     * @param body   订单信息，{ orderId：xxx }
     * @return 订单操作结果
     */
    public Object confirm(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isConfirm()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "订单不能确认收货");
        }

        Short comments = orderGoodsService.getComments(orderId);
        order.setComments(comments);

        order.setOrderStatus(OrderUtil.STATUS_CONFIRM);
        order.setConfirmTime(LocalDateTime.now());
        if (orderService.updateWithOptimisticLocker(order) == 0) {
            return ResponseUtil.updatedDateExpired();
        }
        return ResponseUtil.ok();
    }

    /**
     * 删除订单
     * <p>
     * 1. 检测当前订单是否可以删除；
     * 2. 删除订单。
     *
     * @param userId 用户ID
     * @param body   订单信息，{ orderId：xxx }
     * @return 订单操作结果
     */
    public Object delete(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        Integer orderId = JacksonUtil.parseInteger(body, "orderId");
        if (orderId == null) {
            return ResponseUtil.badArgument();
        }

        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgument();
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.badArgumentValue();
        }

        OrderHandleOption handleOption = OrderUtil.build(order);
        if (!handleOption.isDelete()) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "订单不能删除");
        }

        // 订单order_status没有字段用于标识删除
        // 而是存在专门的delete字段表示是否删除
        orderService.deleteById(orderId);
        // 售后也同时删除
        aftersaleService.deleteByOrderId(userId, orderId);

        return ResponseUtil.ok();
    }

    /**
     * 待评价订单商品信息
     *
     * @param userId  用户ID
     * @param ogid 订单商品ID
     * @return 待评价订单商品信息
     */
    public Object goods(Integer userId, Integer ogid) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }
        LitemallOrderGoods orderGoods = orderGoodsService.findById(ogid);

        if (orderGoods != null) {
            Integer orderId = orderGoods.getOrderId();
            // 实验三修复：订单按下单用户查询并判空，避免订单不存在时 order.getUserId() 抛出 NPE（500/502）
            LitemallOrder order = orderService.findById(userId, orderId);
            if (order == null || !userId.equals(order.getUserId())) {
                return ResponseUtil.badArgument();
            }
        }
        return ResponseUtil.ok(orderGoods);
    }

    /**
     * 评价订单商品
     * <p>
     * 确认商品收货或者系统自动确认商品收货后7天内可以评价，过期不能评价。
     *
     * @param userId 用户ID
     * @param body   订单信息，{ orderId：xxx }
     * @return 订单操作结果
     */
    public Object comment(Integer userId, String body) {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        Integer orderGoodsId = JacksonUtil.parseInteger(body, "orderGoodsId");
        if (orderGoodsId == null) {
            return ResponseUtil.badArgument();
        }
        LitemallOrderGoods orderGoods = orderGoodsService.findById(orderGoodsId);
        if (orderGoods == null) {
            return ResponseUtil.badArgumentValue();
        }
        Integer orderId = orderGoods.getOrderId();
        LitemallOrder order = orderService.findById(userId, orderId);
        if (order == null) {
            return ResponseUtil.badArgumentValue();
        }
        if (!OrderUtil.isConfirmStatus(order) && !OrderUtil.isAutoConfirmStatus(order)) {
            return ResponseUtil.fail(ORDER_INVALID_OPERATION, "当前商品不能评价");
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseUtil.fail(ORDER_INVALID, "当前商品不属于用户");
        }
        Integer commentId = orderGoods.getComment();
        if (commentId == -1) {
            return ResponseUtil.fail(ORDER_COMMENT_EXPIRED, "当前商品评价时间已经过期");
        }
        if (commentId != 0) {
            return ResponseUtil.fail(ORDER_COMMENTED, "订单商品已评价");
        }

        String content = JacksonUtil.parseString(body, "content");
        Integer star = JacksonUtil.parseInteger(body, "star");
        if (star == null || star < 0 || star > 5) {
            return ResponseUtil.badArgumentValue();
        }
        Boolean hasPicture = JacksonUtil.parseBoolean(body, "hasPicture");
        List<String> picUrls = JacksonUtil.parseStringList(body, "picUrls");
        if (hasPicture == null || !hasPicture) {
            picUrls = new ArrayList<>(0);
        }

        // 1. 创建评价
        LitemallComment comment = new LitemallComment();
        comment.setUserId(userId);
        comment.setType((byte) 0);
        comment.setValueId(orderGoods.getGoodsId());
        comment.setStar(star.shortValue());
        comment.setContent(content);
        comment.setHasPicture(hasPicture);
        comment.setPicUrls(picUrls.toArray(new String[]{}));
        commentService.save(comment);

        // 2. 更新订单商品的评价列表
        orderGoods.setComment(comment.getId());
        orderGoodsService.updateById(orderGoods);

        // 3. 更新订单中未评价的订单商品可评价数量
        // 实验三修复：comments 为 null 时先按 0 处理，避免自动拆箱 NPE
        Short commentCount = order.getComments() == null ? 0 : order.getComments();
        if (commentCount > 0) {
            commentCount--;
        }
        order.setComments(commentCount);
        orderService.updateWithOptimisticLocker(order);

        return ResponseUtil.ok();
    }

    /**
     * 取消订单/退款返还优惠券
     * <br/>
     * @param orderId
     * @return void
     * @author Tyson
     * @date 2020/6/8/0008 1:41
     */
    public void releaseCoupon(Integer orderId) {
        List<LitemallCouponUser> couponUsers = couponUserService.findByOid(orderId);
        for (LitemallCouponUser couponUser: couponUsers) {
            // 优惠券状态设置为可使用
            couponUser.setStatus(CouponUserConstant.STATUS_USABLE);
            couponUser.setUpdateTime(LocalDateTime.now());
            couponUserService.update(couponUser);
        }
    }
}