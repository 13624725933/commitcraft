import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class LicenseKeyTool {
    private static final String PRODUCT = "CommitCraft";
    private static final String TOKEN_PREFIX = "CC1";

    private LicenseKeyTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0])) {
            usage();
            return;
        }
        if (!"issue".equals(args[0])) {
            throw new IllegalArgumentException("Unsupported command: " + args[0]);
        }

        Map<String, String> options = parseOptions(args);
        String privateKeyPath = required(options, "--private");
        String buyer = required(options, "--buyer");
        String machine = options.getOrDefault("--machine", "*");
        String expiresAt = expiresAt(options);

        String payload = payload(buyer, machine, expiresAt);
        byte[] signature = sign(payload.getBytes(StandardCharsets.UTF_8), readPrivateKey(Path.of(privateKeyPath)));
        String token = TOKEN_PREFIX + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        System.out.println(token);
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index += 2) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + args[index]);
            }
            options.put(args[index], args[index + 1]);
        }
        return options;
    }

    private static String payload(String buyer, String machine, String expiresAt) {
        LocalDate issuedAt = LocalDate.now();
        return "version=1\n"
                + "product=" + PRODUCT + "\n"
                + "licenseId=" + UUID.randomUUID() + "\n"
                + "buyer=" + encode(buyer) + "\n"
                + "machine=" + encode(machine) + "\n"
                + "issuedAt=" + issuedAt + "\n"
                + "expiresAt=" + expiresAt + "\n";
    }

    private static String expiresAt(Map<String, String> options) {
        if (options.containsKey("--expires")) {
            return options.get("--expires");
        }
        if (options.containsKey("--days")) {
            return LocalDate.now().plusDays(Long.parseLong(options.get("--days"))).toString();
        }
        return "never";
    }

    private static PrivateKey readPrivateKey(Path privateKeyPath) throws Exception {
        byte[] bytes = Files.readAllBytes(privateKeyPath);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.contains("BEGIN")) {
            String base64 = text
                    .replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s+", "");
            bytes = Base64.getDecoder().decode(base64);
        }
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private static byte[] sign(byte[] payload, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(payload);
        return signature.sign();
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void usage() {
        System.out.println("""
                Usage:
                  javac -d build/license-tool tools/license/LicenseKeyTool.java
                  java -cp build/license-tool LicenseKeyTool issue \\
                    --private seller-secrets/commitcraft-private.pkcs8 \\
                    --buyer "buyer name" \\
                    --machine "MACHINE-CODE" \\
                    --days 365

                Expiry:
                  --days 365
                  --expires 2027-12-31
                  omit both for lifetime

                Machine:
                  --machine "*" creates an unbound activation code.
                """);
    }
}
