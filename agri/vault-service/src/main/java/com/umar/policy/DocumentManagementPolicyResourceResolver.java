package com.umar.policy;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class DocumentManagementPolicyResourceResolver implements PolicyResourceResolver {

    private static final String DOCUMENT_RESOURCE = "DOCUMENT";
    private static final String MANAGEMENT_PURPOSE = "GENERAL";

    @Override
    public boolean supports(Method method) {
        return method.getName().equals("listDocuments")
                || method.getName().equals("getDocumentMetadata")
                || method.getName().equals("accessDocument")
                || method.getName().equals("deleteDocument")
                || method.getName().equals("downloadDocument");
    }

    @Override
    public List<PolicyResource> resolve(Object[] arguments) {
        return List.of(new PolicyResource(DOCUMENT_RESOURCE, MANAGEMENT_PURPOSE));
    }
}
