package com.umar.blindindex;


import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BlindIndexVersionResolver {

    private final BlindIndexKeyProvider keyProvider;

    public BlindIndexVersionResolver(
            BlindIndexKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public List<BlindIndexKeyProvider.IndexKeyVersion>
    getLookupVersions(String dataClass) {

        List<BlindIndexKeyProvider.IndexKeyVersion> versions =
                keyProvider.getLookupKeyVersions(dataClass);

        if (versions == null || versions.isEmpty()) {
            throw new IllegalStateException(
                    "No usable blind-index key version for " + dataClass);
        }

        return versions.stream()
                .filter(v ->
                        v.state() == BlindIndexKeyProvider.KeyState.ACTIVE
                                || v.state() == BlindIndexKeyProvider.KeyState.RETIRING)
                .toList();
    }

    public BlindIndexKeyProvider.IndexKeyVersion
    getWriteVersion(String dataClass) {

        BlindIndexKeyProvider.IndexKeyVersion version =
                keyProvider.getActiveWriteKey(dataClass);

        if (version == null
                || version.state()
                != BlindIndexKeyProvider.KeyState.ACTIVE) {

            throw new IllegalStateException(
                    "No active index-key version for " + dataClass);
        }

        return version;
    }
}
