package com.umar.payload.response.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BridgeDispatchResponse {
    private String routedVia;
    private String targetService;
    private String status;
    private Map<String, Object> payload;
}
