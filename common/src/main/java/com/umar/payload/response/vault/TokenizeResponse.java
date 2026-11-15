package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class TokenizeResponse {
    private List<TokenSuccess> tokens;
    private List<ItemFailure> failures;
    private String requestId;
}
