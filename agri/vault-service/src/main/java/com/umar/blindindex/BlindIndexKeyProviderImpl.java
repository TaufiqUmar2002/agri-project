package com.umar.blindindex;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
@Component
public class BlindIndexKeyProviderImpl implements BlindIndexKeyProvider{
    @Override
    public byte[] calculateHmac(UUID keyVersionId, byte[] canonicalValue) {
        return new byte[0];
    }

    @Override
    public List<IndexKeyVersion> getLookupKeyVersions(String dataClass) {
        return List.of();
    }

    @Override
    public IndexKeyVersion getActiveWriteKey(String dataClass) {
        return null;
    }
}
