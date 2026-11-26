package com.umar.integration.domain.enums;

public enum HookType {
    PRE, ENRICH, TRANSFORM, VALIDATE, POST, ADAPTER, SHADOW;

    public boolean isOutcomeChanging() {
        return this == PRE || this == VALIDATE;
    }

    public boolean isSynchronous() {
        return this == PRE || this == ENRICH || this == TRANSFORM || this == VALIDATE || this == ADAPTER;
    }
}
