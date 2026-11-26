package com.umar.blindindex;

import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.CryptoEngineService;
import com.umar.entity.dataProtection.VaultToken;
import com.umar.repository.dataProtection.VaultTokenRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Rebuild source that reads tokens from the vault token store and decrypts them
 * inside the trusted Vault boundary to produce canonical values for re-indexing.
 * Plaintext never leaves this service.
 */
@Component
public class BlindIndexRebuildSourceImpl implements BlindIndexRotationWorker.BlindIndexRebuildSource {

    private final VaultTokenRepository tokenRepository;
    private final CryptoEngineService cryptoEngineService;

    public BlindIndexRebuildSourceImpl(
            VaultTokenRepository tokenRepository,
            CryptoEngineService cryptoEngineService) {
        this.tokenRepository = tokenRepository;
        this.cryptoEngineService = cryptoEngineService;
    }

    @Override
    public List<BlindIndexRotationWorker.RebuildItem> fetchNextBatch(
            String dataClass,
            String afterCursor,
            int batchSize) {

        PageRequest pageable = PageRequest.of(0, batchSize);
        List<VaultToken> tokens;

        if (afterCursor == null || afterCursor.isBlank()) {
            tokens = tokenRepository.findByDataClassOrderByIdAsc(dataClass, pageable);
        } else {
            UUID afterId = UUID.fromString(afterCursor);
            tokens = tokenRepository.findByDataClassAndIdGreaterThanOrderByIdAsc(dataClass, afterId, pageable);
        }

        return tokens.stream()
                .map(t -> new BlindIndexRotationWorker.RebuildItem(
                        t.getId(),
                        t.getDataClass(),
                        t.getSubjectRef(),
                        t.getId().toString()))
                .toList();
    }

    @Override
    public String decryptAndCanonicalize(BlindIndexRotationWorker.RebuildItem item) {
        VaultToken token = tokenRepository.findById(item.tokenId())
                .orElseThrow(() -> new IllegalStateException("Token not found for rebuild: " + item.tokenId()));

        if (token.getWrappedDek() == null) {
            throw new IllegalStateException("Token has no wrapped DEK: " + item.tokenId());
        }

        try (EphemeralSubjectDek dek = cryptoEngineService.unwrapDek(token.getWrappedDek())) {
            return cryptoEngineService.decryptValue(
                    token.getCiphertext(),
                    token.getNonce(),
                    dek,
                    token.getDataClass(),
                    token.getSubjectRef(),
                    token.getToken());
        }
    }

    @Override
    public long countEligibleTokens(String dataClass) {
        return tokenRepository.countByDataClass(dataClass);
    }
}