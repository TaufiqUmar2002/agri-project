package com.umar.service;

import com.umar.payload.request.vault.DeTokenizeRequest;
import com.umar.payload.request.vault.TokenizeRequest;
import com.umar.payload.response.vault.DetokenizeResponse;
import com.umar.payload.response.vault.TokenizeResponse;

public interface IVaultService {

    TokenizeResponse processTokenization(String idempotencyKey, TokenizeRequest request);

    DetokenizeResponse processDeTokenization(String requestId, DeTokenizeRequest request);
}
