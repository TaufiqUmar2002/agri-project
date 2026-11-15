package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TokenFailure {
    private String token;
    private String code; // TOKEN_NOT_FOUND, SUBJECT_ERASED, CLASS_NOT_DETOKENIZABLE
}
