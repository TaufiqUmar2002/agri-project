package com.umar.payload.response.farmer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmerProfileViewResponse {
    private String farmerId;
    private String userId;
    private String status;
    private Map<String, Object> preferences;
    private Map<String, Object> region;
    private String farmSizeBand;
    private List<String> primaryCrops;
    private String kycTier;
    private List<WalletResponse> wallets;
    private Map<String, Object> group;
    private Map<String, Object> plots;
    private Map<String, Object> policies;
    private Map<String, Object> payouts;
    private List<Map<String, Object>> history;
    private Map<String, Object> attributes;
    private Integer version;
}
