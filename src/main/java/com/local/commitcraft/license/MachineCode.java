package com.local.commitcraft.license;

import com.intellij.openapi.application.PathManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MachineCode {
    private MachineCode() {
    }

    public static String current() {
        String raw = String.join("\n",
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                System.getProperty("user.name", ""),
                System.getProperty("user.home", ""),
                PathManager.getConfigPath()
        );
        byte[] digest = sha256(raw);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 16; index++) {
            if (index > 0 && index % 2 == 0) {
                builder.append('-');
            }
            builder.append(String.format("%02X", digest[index]));
        }
        return builder.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
