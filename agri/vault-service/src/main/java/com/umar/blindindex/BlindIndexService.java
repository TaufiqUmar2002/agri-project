package com.umar.blindindex;


import com.umar.entity.dataProtection.BlindIndexEntry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BlindIndexService {

    private final BlindIndexCalculator calculator;
    private final BlindIndexVersionResolver versionResolver;
    private final BlindIndexRepository repository;

    public BlindIndexService(
            BlindIndexCalculator calculator,
            BlindIndexVersionResolver versionResolver,
            BlindIndexRepository repository) {

        this.calculator = calculator;
        this.versionResolver = versionResolver;
        this.repository = repository;
    }

    /**
     * Generates lookup hashes for every key version still in use.
     *
     * This result is internal to the Vault. Do not log it.
     */
    public List<CalculatedIndex> generateIndexes(
            String dataClass,
            String canonicalValue) {

        List<CalculatedIndex> result = new ArrayList<>();

        for (var version :
                versionResolver.getLookupVersions(dataClass)) {

            byte[] hash = calculator.calculate(
                    version.keyVersionId(),
                    canonicalValue);

            result.add(new CalculatedIndex(
                    version.keyVersionId(),
                    version.version(),
                    hash));
        }

        return List.copyOf(result);
    }

    /**
     * Searches for an existing token for the same subject and class.
     */
    @Transactional(readOnly = true)
    public Optional<BlindIndexMatch> findExistingForSubject(
            String dataClass,
            String subjectRef,
            String canonicalValue) {

        for (CalculatedIndex index :
                generateIndexes(dataClass, canonicalValue)) {

            List<BlindIndexEntry> entries =
                    repository
                            .findByDataClassAndSubjectRefAndIndexKeyVersionIdAndIndexHash(
                                    dataClass,
                                    subjectRef,
                                    index.keyVersionId(),
                                    index.hash());

            if (!entries.isEmpty()) {
                BlindIndexEntry entry = entries.get(0);

                return Optional.of(new BlindIndexMatch(
                        entry.getId(),
                        entry.getDataClass(),
                        entry.getSubjectRef(),
                        index.keyVersionId(),
                        index.keyVersion(),
                        true));
            }
        }

        return Optional.empty();
    }

    /**
     * Finds every subject associated with a matching class/value.
     * Use only behind a separately authorized HASH or fraud-detection policy.
     */
    @Transactional(readOnly = true)
    public List<BlindIndexMatch> findAcrossSubjects(
            String dataClass,
            String canonicalValue) {

        Map<UUID, BlindIndexMatch> matches = new LinkedHashMap<>();

        for (CalculatedIndex index :
                generateIndexes(dataClass, canonicalValue)) {

            List<BlindIndexEntry> entries =
                    repository.findByDataClassAndIndexKeyVersionIdAndIndexHash(
                            dataClass,
                            index.keyVersionId(),
                            index.hash());

            for (BlindIndexEntry entry : entries) {
                matches.putIfAbsent(
                        entry.getId(),
                        new BlindIndexMatch(
                                entry.getId(),
                                entry.getDataClass(),
                                entry.getSubjectRef(),
                                index.keyVersionId(),
                                index.keyVersion(),
                                true));
            }
        }

        return List.copyOf(matches.values());
    }

    /**
     * Creates an index using the active write version.
     */
    @Transactional
    public void saveActiveIndex(
            String dataClass,
            String subjectRef,
            UUID tokenId,
            String canonicalValue) {

        var writeVersion =
                versionResolver.getWriteVersion(dataClass);

        byte[] hash = calculator.calculate(
                writeVersion.keyVersionId(),
                canonicalValue);

        saveIndexIfAbsent(
                dataClass,
                subjectRef,
                tokenId,
                writeVersion.keyVersionId(),
                hash);
    }

    /**
     * Backfills an index under a specified version.
     * Used by the rotation worker.
     */
    @Transactional
    public void saveIndexForVersion(
            String dataClass,
            String subjectRef,
            UUID tokenId,
            UUID keyVersionId,
            String canonicalValue) {

        byte[] hash = calculator.calculate(
                keyVersionId,
                canonicalValue);

        saveIndexIfAbsent(
                dataClass,
                subjectRef,
                tokenId,
                keyVersionId,
                hash);
    }

    private void saveIndexIfAbsent(
            String dataClass,
            String subjectRef,
            UUID tokenId,
            UUID keyVersionId,
            byte[] hash) {

        Optional<BlindIndexEntry> existing =
                repository.findByTokenIdAndIndexKeyVersionId(
                        tokenId, keyVersionId);

        if (existing.isPresent()) {
            return;
        }

        try {
//            repository.saveAndFlush(
//                    new BlindIndexEntry(
//                            tokenId,
//                            dataClass,
//                            tokenId,
//                            subjectRef,
//                            keyVersionId,
//                            hash));

        } catch (DataIntegrityViolationException ex) {
            // Another transaction may have inserted the same index.
            // The transaction may be rollback-only here.
            // Do not swallow the exception and continue using this transaction.
            throw ex;
        }
    }

    public record CalculatedIndex(
            UUID keyVersionId,
            int keyVersion,
            byte[] hash) {

        public CalculatedIndex {
            hash = hash.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }
    }
}
