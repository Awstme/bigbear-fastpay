package com.fastpay.controller.pay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastpay.common.BusinessException;
import com.fastpay.common.Constants;
import com.fastpay.dto.CreateOrderDTO;
import com.fastpay.entity.Merchant;
import com.fastpay.entity.Shop;
import com.fastpay.mapper.MerchantMapper;
import com.fastpay.mapper.ShopMapper;
import com.fastpay.service.PayOrderService;
import com.fastpay.util.EpaySignUtil;
import com.fastpay.util.SignUtil;
import com.fastpay.vo.PayResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * 标准易支付（Epay）协议兼容入口
 * 供 New API 等使用 Epay 协议的系统接入
 *
 * @author FastPay
 */
@Slf4j
@Tag(name = "Epay 兼容接口", description = "标准易支付协议接入")
@RestController
public class EpayController {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private final MerchantMapper merchantMapper;
    private final ShopMapper shopMapper;
    private final PayOrderService payOrderService;

    public EpayController(MerchantMapper merchantMapper, ShopMapper shopMapper,
                          PayOrderService payOrderService) {
        this.merchantMapper = merchantMapper;
        this.shopMapper = shopMapper;
        this.payOrderService = payOrderService;
    }

    @Operation(summary = "Epay 页面跳转支付", description = "兼容标准易支付 submit.php 协议")
    @RequestMapping(value = "/submit.php", method = {RequestMethod.GET, RequestMethod.POST})
    public void submit(@RequestParam Map<String, String> params,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String pid = params.get("pid");
            String outTradeNo = params.get("out_trade_no");
            String name = params.get("name");
            String money = params.get("money");
            String payType = params.get("type");
            String notifyUrl = params.get("notify_url");
            String returnUrl = params.get("return_url");

            if (!StringUtils.hasText(pid) || !StringUtils.hasText(outTradeNo)
                    || !StringUtils.hasText(name) || !StringUtils.hasText(money)
                    || !StringUtils.hasText(payType)) {
                throw new BusinessException("缺少必要参数");
            }
            if (outTradeNo.length() > 64) {
                throw new BusinessException("商户订单号过长");
            }
            if (name.length() > 200) {
                throw new BusinessException("商品名称过长");
            }
            if (StringUtils.hasText(notifyUrl) && notifyUrl.length() > 255) {
                throw new BusinessException("回调地址过长");
            }
            if (StringUtils.hasText(returnUrl) && returnUrl.length() > 255) {
                throw new BusinessException("跳转地址过长");
            }
            if (StringUtils.hasText(notifyUrl) && !isHttpUrl(notifyUrl)) {
                throw new BusinessException("回调地址格式不正确");
            }
            if (StringUtils.hasText(returnUrl) && !isHttpUrl(returnUrl)) {
                throw new BusinessException("跳转地址格式不正确");
            }
            String signType = params.get("sign_type");
            if (StringUtils.hasText(signType) && !"MD5".equalsIgnoreCase(signType)) {
                throw new BusinessException("不支持的签名类型");
            }
            if (!Constants.PayType.WXPAY.equals(payType) && !Constants.PayType.ALIPAY.equals(payType)) {
                throw new BusinessException("不支持的支付类型");
            }
            if (!money.matches("\\d+(\\.\\d{1,2})?")) {
                throw new BusinessException("订单金额格式不正确");
            }
            BigDecimal amount = new BigDecimal(money);
            if (amount.compareTo(MIN_AMOUNT) < 0) {
                throw new BusinessException("订单金额最小为0.01元");
            }

            Merchant merchant = findMerchant(pid);
            if (merchant == null) {
                throw new BusinessException("商户不存在");
            }
            if (!Constants.Status.ENABLED.equals(merchant.getStatus())) {
                throw new BusinessException("商户已被禁用");
            }
            if (!EpaySignUtil.verifySign(params, merchant.getApiSecret())) {
                throw new BusinessException("签名验证失败");
            }

            Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                    .eq(Shop::getMerchantId, merchant.getId())
                    .eq(Shop::getStatus, Constants.Status.ENABLED)
                    .orderByAsc(Shop::getId)
                    .last("LIMIT 1"));
            if (shop == null) {
                throw new BusinessException("商户未配置可用店铺");
            }

            long timestamp = System.currentTimeMillis() / 1000;
            CreateOrderDTO dto = new CreateOrderDTO();
            dto.setMerchantNo(merchant.getMerchantNo());
            dto.setOutTradeNo(outTradeNo);
            dto.setShopNo(shop.getShopNo());
            dto.setAmount(amount);
            dto.setSubject(name);
            dto.setPayType(payType);
            dto.setPayMethod(Constants.PayMethod.EPAY);
            dto.setTimestamp(timestamp);
            dto.setReturnUrl(returnUrl);

            TreeMap<String, Object> signParams = new TreeMap<>();
            signParams.put("merchantNo", dto.getMerchantNo());
            signParams.put("outTradeNo", dto.getOutTradeNo());
            signParams.put("shopNo", dto.getShopNo());
            signParams.put("payType", dto.getPayType());
            signParams.put("amount", dto.getAmount().toPlainString());
            signParams.put("subject", dto.getSubject());
            signParams.put("timestamp", String.valueOf(timestamp));
            if (StringUtils.hasText(returnUrl)) {
                signParams.put("returnUrl", returnUrl);
            }
            dto.setSign(SignUtil.generateSign(signParams, merchant.getApiSecret()));

            PayResultVO vo = payOrderService.createOrder(dto, getClientIp(request));
            if (StringUtils.hasText(notifyUrl)) {
                payOrderService.updateNotifyUrl(vo.getOrderNo(), notifyUrl);
            }
            if (StringUtils.hasText(returnUrl)) {
                payOrderService.updateReturnUrl(vo.getOrderNo(), returnUrl);
            }

            log.info("Epay 下单成功: pid={}, outTradeNo={}, type={}, money={}, orderNo={}",
                    pid, outTradeNo, payType, money, vo.getOrderNo());

            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect(vo.getPayPageUrl());
        } catch (BusinessException e) {
            log.warn("Epay 下单失败: {}", e.getMessage());
            writeError(response, e.getMessage());
        } catch (Exception e) {
            log.error("Epay 下单异常", e);
            writeError(response, "系统繁忙，请稍后重试");
        }
    }

    private Merchant findMerchant(String pid) {
        Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantNo, pid));
        if (merchant == null && pid.matches("\\d+")) {
            try {
                merchant = merchantMapper.selectById(Long.valueOf(pid));
            } catch (NumberFormatException ignored) {
                // 超出 Long 范围时按商户不存在处理
            }
        }
        return merchant;
    }

    private boolean isHttpUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<!doctype html><html><head><meta charset=\"utf-8\"><title>支付失败</title></head>"
                + "<body><h3>支付失败</h3><p>" + HtmlUtils.htmlEscape(message) + "</p></body></html>");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
