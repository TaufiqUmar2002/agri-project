package com.umar.auth.security;

import com.umar.auth.config.AuthProperties;
import com.umar.enums.auth.AuthRole;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;

@Service
public class JwtTokenService {

    private static final Set<String> RESERVED_CLAIMS = Set.of(
            "sub", "roles", "kycTier", "lang", "sid", "jti", "iat", "exp", "iss", "aud", "ussd");

    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final KeyPair currentKeyPair;
    private final String currentKid;

    public JwtTokenService(AuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.currentKeyPair = generateRsaKeyPair();
        this.currentKid = "2026-10";
    }

    public String issueAccessToken(
            UUID userId,
            Set<AuthRole> roles,
            String kycTier,
            String lang,
            UUID sessionId,
            int ttlSeconds,
            boolean ussdBound,
            Map<String, Object> customClaims) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", currentKid);

        Map<String, Object> payload = new LinkedHashMap<>();
        if (customClaims != null) {
            customClaims.forEach((k, v) -> {
                if (k != null && k.startsWith("x_") && !RESERVED_CLAIMS.contains(k)) {
                    payload.put(k, v);
                }
            });
        }
        payload.put("sub", userId.toString());
        payload.put("roles", roles.stream().map(Enum::name).sorted().toList());
        payload.put("kycTier", kycTier);
        payload.put("lang", lang);
        payload.put("sid", sessionId.toString());
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", exp.getEpochSecond());
        payload.put("iss", properties.getSecurity().getIssuer());
        payload.put("aud", properties.getSecurity().getAudience());
        if (ussdBound) {
            payload.put("ussd", true);
        }

        try {
            String h64 = b64Url(objectMapper.writeValueAsBytes(header));
            String p64 = b64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = h64 + "." + p64;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(currentKeyPair.getPrivate());
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String s64 = b64Url(sig.sign());
            return signingInput + "." + s64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public Optional<VerifiedJwtClaims> verifyToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            String signingInput = parts[0] + "." + parts[1];
            byte[] sigBytes = Base64.getUrlDecoder().decode(parts[2]);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(currentKeyPair.getPublic());
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(sigBytes)) return Optional.empty();

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(payloadBytes);
            long exp = node.get("exp").asLong();
            if (Instant.now().getEpochSecond() >= exp) return Optional.empty();

            Set<AuthRole> roles = EnumSet.noneOf(AuthRole.class);
            if (node.has("roles") && node.get("roles").isArray()) {
                for (JsonNode r : node.get("roles")) {
                    roles.add(AuthRole.valueOf(r.asString()));
                }
            }
            return Optional.of(new VerifiedJwtClaims(
                    node.get("sub").asString(),
                    roles,
                    node.path("kycTier").asString("TIER_0"),
                    node.path("lang").asString("en"),
                    node.path("sid").asString(null)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Map<String, Object> getJwks() {
        RSAPublicKey pub = (RSAPublicKey) currentKeyPair.getPublic();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", currentKid);
        jwk.put("n", b64UrlUnsigned(pub.getModulus()));
        jwk.put("e", b64UrlUnsigned(pub.getPublicExponent()));
        return Map.of("keys", List.of(jwk));
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm unavailable", e);
        }
    }

    private String b64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String b64UrlUnsigned(BigInteger val) {
        byte[] bytes = val.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return b64Url(bytes);
    }

    public record VerifiedJwtClaims(String sub, Set<AuthRole> roles, String kycTier, String lang, String sid) {}
}
