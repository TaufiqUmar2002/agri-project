package com.umar.keymanagement;

public record DataKeyMaterial(byte[] plaintextKey, byte[] encryptedKey) {

}
