//package com.umar.crypto;
//
//import com.umar.keymanagement.DataKeyMaterial;
//import com.umar.keymanagement.KeyManagementProvider;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
////@Service
////@RequiredArgsConstructor
//public class DataEncryptionService {
//
//    private final KeyManagementProvider keyManagementProvider;
//
//
//
//    public DataKeyMaterial generateKey(String keyId) {
//        return keyManagementProvider.generateDataKey(keyId);
//    }
//
//    public byte[] decryptKey(
//            String keyId,
//            byte[] encryptedKey) {
//        return keyManagementProvider.decryptDataKey(
//                keyId,
//                encryptedKey
//        );
//    }
//}
