package com.nextwindow.notification;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;

import static com.nextwindow.notification.WebPushCrypto.b64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    /** Test vector from RFC 8291, Appendix A. */
    @Test
    void matchesRfc8291TestVector() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.US_ASCII);
        KeyPair as = new KeyPair(
                WebPushCrypto.publicKey(b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")),
                WebPushCrypto.privateKey(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));
        byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] out = WebPushCrypto.encrypt(plaintext, uaPublic, b64("BTBZMqHH6r4Tts7J_aSIgg"), as, b64("DGv6ra1nlYgDCS1FRnbzlw"));
        assertThat(b64(out)).isEqualTo("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void vapidHeaderIsVerifiableJwt() throws Exception {
        KeyPair kp = WebPushCrypto.generateKeyPair();
        String pub = b64(WebPushCrypto.encode((java.security.interfaces.ECPublicKey) kp.getPublic()));
        byte[] d = ((java.security.interfaces.ECPrivateKey) kp.getPrivate()).getS().toByteArray();
        byte[] d32 = new byte[32];
        System.arraycopy(d, Math.max(0, d.length - 32), d32, Math.max(0, 32 - d.length), Math.min(32, d.length));
        String priv = b64(d32);
        WebPushCrypto.validateVapidKeys(pub, priv);

        String header = WebPushCrypto.vapidHeader("https://fcm.googleapis.com", "mailto:ops@example.com", 1_900_000_000L, pub, priv);
        assertThat(header).startsWith("vapid t=").endsWith(", k=" + pub);
        String jwt = header.substring(8, header.indexOf(','));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"exp\":1900000000");
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(kp.getPublic());
        s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(s.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void rejectsMismatchedKeys() throws Exception {
        KeyPair a = WebPushCrypto.generateKeyPair();
        String pub = b64(WebPushCrypto.encode((java.security.interfaces.ECPublicKey) a.getPublic()));
        assertThatThrownBy(() -> WebPushCrypto.validateVapidKeys(pub, b64(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32}))).isInstanceOf(Exception.class);
    }

    @Test
    void endpointAllowListBlocksArbitraryHosts() {
        assertThat(WebPushSender.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(WebPushSender.isAllowedEndpoint("https://updates.push.services.mozilla.com/wpush/v2/x")).isTrue();
        assertThat(WebPushSender.isAllowedEndpoint("https://web.push.apple.com/QX")).isTrue();
        assertThat(WebPushSender.isAllowedEndpoint("http://fcm.googleapis.com/x")).isFalse();
        assertThat(WebPushSender.isAllowedEndpoint("https://169.254.169.254/latest")).isFalse();
        assertThat(WebPushSender.isAllowedEndpoint("https://evil.com/fcm.googleapis.com")).isFalse();
        assertThat(WebPushSender.isAllowedEndpoint("https://fcm.googleapis.com.evil.com/x")).isFalse();
    }
}
