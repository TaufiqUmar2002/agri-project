package com.umar.payload.response.trigger;

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
public class TriggerEventsResponse {
    private List<Map<String, Object>> events;
    private Map<String, Object> summary;
    private int page;
    private int size;
    private long total;
}
