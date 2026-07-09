package com.local.commitcraft.license;

public record LicenseInfo(
        String licenseId,
        String buyer,
        String machineCode,
        String issuedAt,
        String expiresAt
) {
    public boolean lifetime() {
        return "never".equalsIgnoreCase(expiresAt);
    }
}
