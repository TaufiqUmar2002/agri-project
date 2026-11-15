package com.umar.service;

import com.umar.config.EphemeralSubjectDek;
import com.umar.crypto.*;
import com.umar.entity.dataProtection.VaultToken;
import com.umar.payload.request.vault.DeTokenizeRequest;
import com.umar.payload.request.vault.TokenizeRequest;
import com.umar.payload.response.vault.DetokenizeResponse;
import com.umar.payload.response.vault.ItemFailure;
import com.umar.payload.response.vault.TokenSuccess;
import com.umar.payload.response.vault.TokenizeResponse;
import com.umar.repository.dataProtection.VaultTokenRepository;
import com.umar.util.TokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService implements IVaultService{

    private final VaultTokenRepository tokenRepository;
    private final TokenGenerator tokenGenerator;
    private final CryptoEngineService cryptoEngineService;
    private final ValidationRegistryService validatorService;
    private final CryptoService cryptoService;
//    private final DataEncryptionKeyProvider keyProvider;

    @Override
    public TokenizeResponse processTokenization(String idempotencyKey, TokenizeRequest request) {
        String operationRequestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        List<TokenSuccess> successList = new ArrayList<>();
        List<ItemFailure> failureList = new ArrayList<>();
//        EphemeralSubjectDek ephemeralSubjectDek = new EphemeralSubjectDek();
        for (int index = 0; index < request.getItems().size(); index++) {
            TokenizeRequest.Items item = request.getItems().get(index);
            if (!validatorService.isValidForClass(item.getValue(), item.getDataClass())) {
                failureList.add(new ItemFailure(index, "VALUE_INVALID", "Value fails the structural verification constraints of class"));
                continue;
            }
            boolean reuse = (item.getReuseExisting() == null) || item.getReuseExisting();
            if (reuse) {
                Optional<VaultToken> existingToken = tokenRepository.findExistingMatch(item.getSubjectRef(), item.getDataClass());
                if (existingToken.isPresent()) {
                    VaultToken t = existingToken.get();
                    successList.add(new TokenSuccess(index, t.getToken(), t.getDataClass(), t.getMaskedForm(), true, t.getFormat(), t.getCreatedAt()));
                    continue;
                }
            }
            // FPE Restriction Verification Check (Scenario 4)
            if ("FPE".equalsIgnoreCase(item.getFormat().name()) && !validatorService.isFpePermitted(item.getDataClass())) {
                failureList.add(new ItemFailure(index, "FPE_NOT_ALLOWED", "Format preserving option unavailable for data class allocation"));
                continue;
            }

            // Step 4 & 6: Resolve Keys & Execute Cipher operations inside clearable memory
            try (EphemeralSubjectDek activeDek = cryptoEngineService.generateEphemeralDek()) {
                String mintedTokenStr = tokenGenerator.mintTokenString(item.getDataClass(), false);
                String hint = tokenGenerator.createMaskedHint(mintedTokenStr);

                var cryptoPayload = cryptoEngineService.encryptValue(
                        item.getValue(), activeDek, item.getDataClass(), item.getSubjectRef(), mintedTokenStr
                );
                    VaultToken newToken = VaultToken.builder()
                            .id(UUID.randomUUID())
                            .token(mintedTokenStr)
                            .dataClass(item.getDataClass())
                            .subjectRef(item.getSubjectRef())
                            .retentionGroup("ERASABLE")
                            .ciphertext(cryptoPayload.ciphertext())
                            .nonce(cryptoPayload.nonce())
                            .keyVersion(cryptoPayload.keyVersion())
                            .maskedForm(hint)
                            .format("OPAQUE")
                            .createdAt(Instant.now())
                            .build();
                // Step 7 & 8: Store encrypted token state blocks and bind index records
                tokenRepository.save(newToken);
                successList.add(new TokenSuccess(index, mintedTokenStr, item.getDataClass(), hint, false, "OPAQUE", newToken.getCreatedAt()));
            }
        }
        // 2. Resolve active DEK for subject and data class
//        DataEncryptionKeyProvider.KeyMaterial keyMaterial = keyProvider.getEncryptionKey(
//                request.subjectRef(),
//                request.dataClass()
//        );

        // 3. Build authenticated encryption context
//        EncryptionContext context = new EncryptionContext(
//                request.dataClass(),
//                request.subjectRef(),
//                token
//        );

        // 4. Encrypt plaintext
//        EncryptedPayload encryptedPayload = cryptoService.encrypt(
//                request.value(),
//                keyMaterial.key(),
//                context
//        );

        // 5. Persist only ciphertext, nonce and key metadata
//        VaultToken vaultToken = new VaultToken(
//                token,
//                request.dataClass(),
//                request.subjectRef(),
//                encryptedPayload.ciphertext(),
//                encryptedPayload.nonce(),
//                keyMaterial.keyVersionId()
//        );

        // 6. Save encrypted record
//        VaultToken savedToken = tokenRepository.save(vaultToken);

        // Step 9: Commit WORM Append Log Footprint and Emit Outbox telemetry
        // ... Log commitment steps go here ...

        return new TokenizeResponse(successList, failureList, operationRequestId);
    }

    @Override
    public DetokenizeResponse processDeTokenization(String requestId, DeTokenizeRequest request) {
        return null;
    }

    private void validateIdempotencyKey(String key) {

        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be a valid UUID"
            );
        }
    }
    }

//    8443
