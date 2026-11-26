package com.umar.payload.response.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccessRecordResponse {
    private UUID id;
    private UUID recordId;
    private String caller;
    private String operation;
    private String dataClass;
    private String secretRef;
    private String purpose;
    private String contextRefHash;
    private String subjectHash;
    private String count;
    private String outcome;
    private String reasonCode;
    private String timestamp;
}
