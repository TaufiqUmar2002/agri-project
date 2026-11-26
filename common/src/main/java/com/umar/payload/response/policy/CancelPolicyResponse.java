package com.umar.payload.response.policy;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CancelPolicyResponse {
    private String policyId;
    private String status;
    private String cancelledAt;
    private String reasonCode;
    private Map<String, Object> refund;
    private Long subsidyReleasedMinor;
    private String coverEndedAt;
    private String message;
}
