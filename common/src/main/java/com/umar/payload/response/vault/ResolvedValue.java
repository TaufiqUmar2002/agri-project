package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResolvedValue {
    private String token;
    private String dataClass;
    private String value;
}
