package com.local.commitcraft.license;

public record LicenseCheck(boolean valid, String message, LicenseInfo info) {
    public static LicenseCheck valid(LicenseInfo info) {
        String expiry = info.lifetime() ? "永久" : info.expiresAt();
        return new LicenseCheck(true, "已激活：" + info.buyer() + "，有效期至 " + expiry, info);
    }

    public static LicenseCheck invalid(String message) {
        return new LicenseCheck(false, message, null);
    }
}
