package com.umar.payload.response.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderStatusResponse {
    private String provider;
    private String status;
    private String previousStatus;
    private Map<String, Object> probe;
    private int queuedAffectedCount;
    private String updatedAt;
    private int version;
}
