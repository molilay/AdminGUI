package me.admin.gui.manager;

import me.admin.gui.AdvancedModeratorGUI;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** HMAC authenticity for YAML artifacts. The key is deliberately excluded from backups. */
public final class ArtifactSignatureService {

    private static final String VERSION = "AMGUI-HMAC-SHA256-V1";
    private final byte[] key;
    private final String keyId;

    public ArtifactSignatureService(AdvancedModeratorGUI plugin) {
        Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        this.key = loadOrCreate(root.resolve("artifact-signing.key"), root);
        this.keyId = keyId(key);
    }

    public String keyId() { return keyId; }

    public String sign(byte[] payload) { return hmac(key, payload); }

    public boolean verify(byte[] payload, String expected) {
        if (expected == null || !expected.matches("[0-9a-fA-F]{64}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(sign(payload)));
    }

    public String envelope(byte[] payload) {
        return envelope(key, payload);
    }

    public boolean verifyEnvelope(byte[] payload, String envelope) {
        return verifyEnvelope(key, payload, envelope);
    }

    static String envelope(byte[] key, byte[] payload) {
        return VERSION + "|" + keyId(key) + "|" + hmac(key, payload) + "\n";
    }

    static boolean verifyEnvelope(byte[] key, byte[] payload, String envelope) {
        if (envelope == null) return false;
        String[] fields = envelope.trim().split("\\|", -1);
        if (fields.length != 3 || !VERSION.equals(fields[0]) || !keyId(key).equals(fields[1])
                || !fields[2].matches("[0-9a-fA-F]{64}")) return false;
        byte[] expected = HexFormat.of().parseHex(hmac(key, payload));
        byte[] actual = HexFormat.of().parseHex(fields[2]);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String keyId(byte[] key) {
        return HexFormat.of().formatHex(sha256(key)).substring(0, 16);
    }

    static String hmac(byte[] key, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception error) {
            throw new IllegalStateException("HmacSHA256 unavailable", error);
        }
    }

    private static byte[] loadOrCreate(Path keyFile, Path root) {
        try {
            if (Files.isRegularFile(keyFile)) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.US_ASCII).trim());
                if (decoded.length >= 32) return decoded;
                throw new IOException("artifact-signing.key is too short");
            }
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            YamlPersistenceService.atomicWrite(keyFile, Base64.getEncoder().encode(generated), root);
            return generated;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load artifact signing key: " + error.getMessage(), error);
        }
    }

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
