package com.umar.payload.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRightsResponse {
    private String requestId;
    private String type;
    private String status;
    private String requestedAt;
    private String dueBy;
    private String coolingOffUntil;
    private String notice;
}
