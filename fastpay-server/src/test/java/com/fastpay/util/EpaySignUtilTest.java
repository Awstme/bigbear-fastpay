package com.fastpay.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpaySignUtilTest {

    private static final String KEY = "testsecret1234567890";
    private static final String EXPECTED_SIGN = "31ec0f276f658e30f383fcfd3cfffe28";

    private Map<String, String> baseParams() {
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
        return params;
    }

    @Test
    void generateSignMatchesStandardEpayProtocol() {
        assertEquals(EXPECTED_SIGN, EpaySignUtil.generateSign(baseParams(), KEY));
    }

    @Test
    void verifySignIgnoresSignTypeAndEmptyValues() {
        Map<String, String> params = baseParams();
        params.put("empty", "");
        params.put("sign", EXPECTED_SIGN);

        assertTrue(EpaySignUtil.verifySign(params, KEY));

        params.put("money", "0.02");
        assertFalse(EpaySignUtil.verifySign(params, KEY));
    }
}
