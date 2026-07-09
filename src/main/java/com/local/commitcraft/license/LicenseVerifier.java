package com.local.commitcraft.license;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LicenseVerifier {
    private static final String PRODUCT = "CommitCraft";
    private static final String TOKEN_PREFIX = "CC1";
    private static final String PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAwN/mWmO5FhGeZejc5P0WoPGeYCb6jv2IDFPeknUTDU4=";

    public LicenseCheck verify(String activationCode, String machineCode) {
        String token = activationCode == null ? "" : activationCode.replaceAll("\\s+", "");
        if (token.isEmpty()) {
            return LicenseCheck.invalid("未激活。复制机器码给卖家获取激活码。");
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !TOKEN_PREFIX.equals(parts[0])) {
            return LicenseCheck.invalid("激活码格式不正确。");
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (!verifySignature(payload, signature)) {
                return LicenseCheck.invalid("激活码签名无效。");
            }

            Map<String, String> values = parsePayload(new String(payload, StandardCharsets.UTF_8));
            if (!PRODUCT.equals(values.get("product"))) {
                return LicenseCheck.invalid("激活码不适用于 CommitCraft。");
            }

            String licensedMachine = values.getOrDefault("machine", "");
            if (!"*".equals(licensedMachine) && !licensedMachine.equals(machineCode)) {
                return LicenseCheck.invalid("激活码不属于当前机器。");
            }

            LicenseInfo info = new LicenseInfo(
                    values.getOrDefault("licenseId", ""),
                    values.getOrDefault("buyer", "unknown"),
                    licensedMachine,
                    values.getOrDefault("issuedAt", ""),
                    values.getOrDefault("expiresAt", "")
            );
            if (expired(info.expiresAt())) {
                return LicenseCheck.invalid("激活码已过期。");
            }
            return LicenseCheck.valid(info);
        } catch (Exception exception) {
            return LicenseCheck.invalid("激活码无法解析。");
        }
    }

    private boolean verifySignature(byte[] payload, byte[] signatureBytes) throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_BASE64))
        );
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(publicKey);
        signature.update(payload);
        return signature.verify(signatureBytes);
    }

    private Map<String, String> parsePayload(String payload) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : payload.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(line.substring(0, separator),
                    URLDecoder.decode(line.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return values;
    }

    private boolean expired(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank() || "never".equalsIgnoreCase(expiresAt)) {
            return false;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return today.isAfter(LocalDate.parse(expiresAt));
    }
}
