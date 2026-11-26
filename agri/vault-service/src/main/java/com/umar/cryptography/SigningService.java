package com.umar.cryptography;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates and signs/verifies using HMAC-SHA256, RS256 (RSA), and ES256 (EC P-256)
 * inside the vault cryptographic boundary (VLT-03, VLT-11, VLT-15).
 */
@Service
@RequiredArgsConstructor
public class SigningService {

    private final HmacService hmacService;
    private final SecureRandom secureRandom = new SecureRandom();

    public record GeneratedKeyMaterial(
            byte[] privateOrSymmetricBytes,
            String algorithm,
            int keySizeBits,
            String kid,
            Map<String, Object> publicKeyJwk) {
    }

    public GeneratedKeyMaterial generateKey(String algorithm, Integer requestedSizeBits, String kid) {
        String alg = normalizeAlgorithm(algorithm);
        try {
            return switch (alg) {
                case "HMAC_SHA256" -> {
                    int bits = requestedSizeBits != null && requestedSizeBits >= 128 ? requestedSizeBits : 256;
                    byte[] raw = new byte[bits / 8];
                    secureRandom.nextBytes(raw);
                    yield new GeneratedKeyMaterial(raw, "HMAC_SHA256", bits, kid, null);
                }
                case "AES_256" -> {
                    int bits = 256;
                    byte[] raw = new byte[32];
                    secureRandom.nextBytes(raw);
                    yield new GeneratedKeyMaterial(raw, "AES_256", bits, kid, null);
                }
                case "RS256", "RSA_2048", "RSA_3072" -> {
                    int bits = "RSA_3072".equals(alg) ? 3072 : (requestedSizeBits != null && requestedSizeBits >= 2048 ? requestedSizeBits : 2048);
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                    kpg.initialize(bits, secureRandom);
                    KeyPair pair = kpg.generateKeyPair();
                    RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
                    Map<String, Object> jwk = new LinkedHashMap<>();
                    jwk.put("kty", "RSA");
                    jwk.put("kid", kid);
                    jwk.put("alg", "RS256");
                    jwk.put("use", "sig");
                    jwk.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray()));
                    jwk.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray()));
                    jwk.put("spkiBase64", Base64.getEncoder().encodeToString(pub.getEncoded()));
                    yield new GeneratedKeyMaterial(pair.getPrivate().getEncoded(), "RS256", bits, kid, jwk);
                }
                case "ES256", "EC_P256" -> {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                    kpg.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
                    KeyPair pair = kpg.generateKeyPair();
                    ECPublicKey pub = (ECPublicKey) pair.getPublic();
                    Map<String, Object> jwk = new LinkedHashMap<>();
                    jwk.put("kty", "EC");
                    jwk.put("crv", "P-256");
                    jwk.put("kid", kid);
                    jwk.put("alg", "ES256");
                    jwk.put("use", "sig");
                    jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getW().getAffineX().toByteArray()));
                    jwk.put("y", Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getW().getAffineY().toByteArray()));
                    jwk.put("spkiBase64", Base64.getEncoder().encodeToString(pub.getEncoded()));
                    yield new GeneratedKeyMaterial(pair.getPrivate().getEncoded(), "ES256", 256, kid, jwk);
                }
                default -> throw new IllegalArgumentException("Unsupported key generation algorithm: " + algorithm);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Failed to generate key material for algorithm " + algorithm, e);
        }
    }

    public byte[] sign(String algorithm, byte[] keyMaterial, byte[] payload) {
        String alg = normalizeAlgorithm(algorithm);
        try {
            return switch (alg) {
                case "HMAC_SHA256" -> hmacService.sign(keyMaterial, payload);
                case "RS256", "RSA_2048", "RSA_3072" -> {
                    PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyMaterial));
                    Signature sig = Signature.getInstance("SHA256withRSA");
                    sig.initSign(pk, secureRandom);
                    sig.update(payload);
                    yield sig.sign();
                }
                case "ES256", "EC_P256" -> {
                    PrivateKey pk = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyMaterial));
                    Signature sig = Signature.getInstance("SHA256withECDSA");
                    sig.initSign(pk, secureRandom);
                    sig.update(payload);
                    yield sig.sign();
                }
                default -> throw new IllegalArgumentException("Unsupported signing algorithm: " + algorithm);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Signing operation failed", e);
        }
    }

    public boolean verify(String algorithm, byte[] keyMaterial, String spkiPublicKeyBase64, byte[] payload, byte[] signature) {
        if (signature == null) {
            return false;
        }
        String alg = normalizeAlgorithm(algorithm);
        try {
            return switch (alg) {
                case "HMAC_SHA256" -> hmacService.verify(keyMaterial, payload, signature);
                case "RS256", "RSA_2048", "RSA_3072" -> {
                    byte[] pubBytes = Base64.getDecoder().decode(spkiPublicKeyBase64);
                    PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
                    Signature sig = Signature.getInstance("SHA256withRSA");
                    sig.initVerify(pub);
                    sig.update(payload);
                    yield sig.verify(signature);
                }
                case "ES256", "EC_P256" -> {
                    byte[] pubBytes = Base64.getDecoder().decode(spkiPublicKeyBase64);
                    PublicKey pub = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(pubBytes));
                    Signature sig = Signature.getInstance("SHA256withECDSA");
                    sig.initVerify(pub);
                    sig.update(payload);
                    yield sig.verify(signature);
                }
                default -> throw new IllegalArgumentException("Unsupported verification algorithm: " + algorithm);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    public static String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return "HMAC_SHA256";
        }
        return algorithm.trim().toUpperCase().replace('-', '_');
    }
}
