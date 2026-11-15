package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DetokenizeResponse {
    private List<ResolvedValue> values;
    private List<TokenFailure> failures;
    private String requestId;
}
