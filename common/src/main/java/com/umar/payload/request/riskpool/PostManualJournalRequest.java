package com.umar.payload.request.riskpool;

import com.umar.enums.riskpool.JournalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class PostManualJournalRequest {
    @NotNull
    private JournalType journalType;
    @NotNull
    private LocalDate valueDate;
    @NotEmpty
    private List<Map<String, Object>> lines;
    @NotBlank
    private String currency;
    private String reverses;
    @NotBlank
    private String reasonCode;
    @NotEmpty
    private List<String> evidenceTokens;
    @NotBlank
    @Size(min = 20, max = 500)
    private String note;
}
