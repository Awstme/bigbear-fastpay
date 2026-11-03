package com.fastpay.controller.pay;

import com.fastpay.dto.CreateOrderDTO;
import com.fastpay.entity.Merchant;
import com.fastpay.entity.Shop;
import com.fastpay.mapper.MerchantMapper;
import com.fastpay.mapper.ShopMapper;
import com.fastpay.service.PayOrderService;
import com.fastpay.vo.PayResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EpayControllerTest {

    private static final String API_SECRET = "testsecret1234567890";
    private static final String VALID_SIGN = "31ec0f276f658e30f383fcfd3cfffe28";

    private PayOrderService payOrderService;
    private EpayController controller;

    @BeforeEach
    void setUp() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        ShopMapper shopMapper = mock(ShopMapper.class);
        payOrderService = mock(PayOrderService.class);

        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setMerchantNo("M100000000001");
        merchant.setApiSecret(API_SECRET);
        merchant.setStatus(1);
        when(merchantMapper.selectOne(any())).thenReturn(merchant);

        Shop shop = new Shop();
        shop.setId(2L);
        shop.setMerchantId(1L);
        shop.setShopNo("S100000000001");
        shop.setStatus(1);
        when(shopMapper.selectOne(any())).thenReturn(shop);

        controller = new EpayController(merchantMapper, shopMapper, payOrderService);
    }

    private Map<String, String> validParams() {
        Map<String, String> params = new HashMap<>();
        params.put("pid", "M100000000001");
        params.put("type", "wxpay");
        params.put("out_trade_no", "EPAYTEST0001");
        params.put("notify_url", "https://newapi.example.com/api/user/epay/notify");
        params.put("name", "Test Recharge");
        params.put("money", "0.01");
        params.put("device", "pc");
        params.put("sign_type", "MD5");
        params.put("return_url", "https://newapi.example.com/console/topup");
        params.put("sign", VALID_SIGN);
        return params;
    }

    @Test
    void submitWithValidSignRedirectsToPayPage() throws Exception {
        PayResultVO result = new PayResultVO();
        result.setOrderNo("FP20260101000000001");
        result.setPayPageUrl("https://pay.example.com/pay/FP20260101000000001");
        when(payOrderService.createOrder(any(), anyString())).thenReturn(result);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.submit(validParams(), request, response);

        assertEquals(302, response.getStatus());
        assertEquals("https://pay.example.com/pay/FP20260101000000001", response.getRedirectedUrl());
        verify(payOrderService).updateNotifyUrl("FP20260101000000001",
                "https://newapi.example.com/api/user/epay/notify");
        verify(payOrderService).updateReturnUrl("FP20260101000000001",
                "https://newapi.example.com/console/topup");

        ArgumentCaptor<CreateOrderDTO> captor = ArgumentCaptor.forClass(CreateOrderDTO.class);
        verify(payOrderService).createOrder(captor.capture(), anyString());
        assertEquals("epay", captor.getValue().getPayMethod());
        assertEquals("S100000000001", captor.getValue().getShopNo());
    }

    @Test
    void submitWithInvalidSignReturnsBadRequest() throws Exception {
        Map<String, String> params = validParams();
        params.put("sign", "bad-sign");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.submit(params, request, response);

        assertEquals(400, response.getStatus());
        verify(payOrderService, never()).createOrder(any(), anyString());
    }
}
