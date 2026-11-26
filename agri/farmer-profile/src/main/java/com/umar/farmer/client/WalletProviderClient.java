package com.umar.farmer.client;

import java.util.Optional;

public interface WalletProviderClient {
    Optional<String> lookupAccountHolderName(String provider, String walletNumber);
}
