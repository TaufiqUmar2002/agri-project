package com.umar.farmer.client;

import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalWalletProviderClient implements WalletProviderClient {

    @Setter
    private volatile boolean unavailable = false;

    private final Map<String, String> directory = new ConcurrentHashMap<>();

    @Override
    public Optional<String> lookupAccountHolderName(String provider, String walletNumber) {
        if (unavailable) {
            return Optional.empty();
        }
        return Optional.of(directory.getOrDefault(walletNumber, "AMINA ODHIAMBO"));
    }

    public void registerAccountHolder(String walletNumber, String registeredName) {
        directory.put(walletNumber, registeredName);
    }
}
