package com.umar.payload.request.audit;

import com.umar.enums.audit.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RegulatoryReportRequest {
    @NotNull
    private ReportType reportType;
    private String mode = "ONE_OFF"; // ONE_OFF | SCHEDULE | APPROVE | CANCEL_SCHEDULE
    private Map<String, String> period;
    private Map<String, Object> schedule;
    @NotBlank
    private String format;
    private Map<String, Object> filters;
    private List<String> recipients;
    private String reportId;
    private String reportHash;
}
