package com.umar.payload.response.policy;

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
public class RenewalsResponse {
    private List<Map<String, Object>> items;
    private Map<String, Object> aggregates;
    private String asOf;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
