package com.umar.payload.response.plot;

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
public class RecordSeasonResponse {
    private String plotId;
    private Map<String, Object> season;
    private String lockDate;
    private List<String> flags;
    private Integer version;
}
