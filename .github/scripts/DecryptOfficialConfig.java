import java.nio.file.*;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Load only the official backend. Missing production configuration blocks release builds. */
class DecryptOfficialConfig {
    public static void main(String[] args) throws Exception {
        Properties envelope = new Properties();
        try (var input = Files.newInputStream(Path.of(args[0]))) { envelope.load(input); }
        char[] password = System.getenv("ANDROID_KEYSTORE_PASSWORD").toCharArray();
        KeyStore store = KeyStore.getInstance(Path.of(System.getenv("ANDROID_KEYSTORE_PATH")).toFile(), password);
        PrivateKey privateKey = (PrivateKey) store.getKey("swu-release", password);
        Arrays.fill(password, '\0');
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] key = rsa.doFinal(Base64.getDecoder().decode(envelope.getProperty("wrapped_key")));
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(envelope.getProperty("nonce"))));
        aes.updateAAD("fr.swucollection.app/config-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] xml = aes.doFinal(Base64.getDecoder().decode(envelope.getProperty("ciphertext")));
        Path target = Path.of("app/src/release/res/values/community_config.xml");
        Files.createDirectories(target.getParent());
        Files.write(target, xml);
        Arrays.fill(key, (byte)0);
        System.out.println("Official backend configuration prepared.");
    }
}
