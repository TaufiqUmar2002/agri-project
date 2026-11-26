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
public class FarmerProfileResponse {
    private String farmerId;
    private String userId;
    private String status;
    private Map<String, Object> preferences;
    private Map<String, Object> region;
    private String farmSizeBand;
    private List<String> primaryCrops;
    private String groupId;
    private WalletResponse wallet;
    private List<String> changedFields;
    private Map<String, Object> attributes;
    private Integer version;
    private String createdAt;
    private String updatedAt;
}
