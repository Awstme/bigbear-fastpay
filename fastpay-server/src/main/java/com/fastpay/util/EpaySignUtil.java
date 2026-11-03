package com.fastpay.util;

import cn.hutool.crypto.SecureUtil;

import java.util.Map;
import java.util.TreeMap;

/**
 * 标准易支付（Epay）协议签名工具
 * 签名规则：过滤 sign、sign_type 和空值，按参数名升序拼接为 a=b&c=d，
 * 末尾直接追加商户密钥后做 MD5（小写）
 *
 * @author FastPay
 */
public class EpaySignUtil {

    public static String generateSign(Map<String, String> params, String key) {
        TreeMap<String, String> sortedParams = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || "sign".equals(name) || "sign_type".equals(name)
                    || value == null || value.isEmpty()) {
                continue;
            }
            sortedParams.put(name, value);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }

        return SecureUtil.md5(sb.toString() + key);
    }

    public static boolean verifySign(Map<String, String> params, String key) {
        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        return sign.equalsIgnoreCase(generateSign(params, key));
    }
}
