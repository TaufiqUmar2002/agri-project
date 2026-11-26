package com.umar.payload.response.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingApprovalResponse {
    private String approvalId;
    private String type;
    private String subjectId;
    private String status;
    private List<Map<String, Object>> decisions;
    private List<String> remaining;
    private String subjectStatus;
    private String subjectHash;
}
