package com.umar.payload.request.vault;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class StoreDocumentRequest {

    @NotBlank(message = "Data class is required")
    private String dataClass;

    @NotBlank(message = "Purpose is required")
    private String purpose;

    @NotBlank(message = "Subject reference is required")
    private String subjectRef;

    private String ownerRef;

    private String retentionClass;

    private String contentType;

    private String contextRef;

    private String sha256;

    private Map<String, Object> captureMeta;

    @Size(max = 100000, message = "Document content cannot exceed 100000 characters")
    private String content;
}
