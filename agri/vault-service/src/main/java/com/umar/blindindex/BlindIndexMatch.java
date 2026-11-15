package com.umar.blindindex;


import java.util.UUID;

public record BlindIndexMatch(
        UUID tokenId,
        String dataClass,
        String subjectRef,
        UUID matchedKeyVersionId,
        int matchedKeyVersion,
        boolean matched) {
}
