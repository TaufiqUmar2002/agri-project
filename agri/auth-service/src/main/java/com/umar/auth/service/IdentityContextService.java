package com.umar.auth.service;

import com.umar.auth.domain.entity.ConsentRecordEntity;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.exception.AuthApiException;
import com.umar.auth.repository.ConsentRecordRepository;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.enums.auth.ConsentPurpose;
import com.umar.enums.auth.ConsentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class IdentityContextService {

    private static final long CACHE_TTL_SECONDS = 30L;

    private final UserAccountRepository userAccountRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final Map<UUID, CachedContext> cache = new ConcurrentHashMap<>();

    public IdentityContextSnapshot getIdentityContext(UUID userId, List<String> requestedPurposes, boolean bypassCache) {
        Instant now = Instant.now();
        CachedContext cached = bypassCache ? null : cache.get(userId);
        IdentityContextSnapshot base;
        if (cached != null && now.isBefore(cached.expiresAt())) {
            base = cached.snapshot();
        } else {
            UserAccountEntity user = userAccountRepository.findById(userId)
                    .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

            Map<ConsentPurpose, ConsentRecordEntity> latestByPurpose = new EnumMap<>(ConsentPurpose.class);
            for (ConsentRecordEntity rec : consentRecordRepository.findByUserIdOrderByGrantedAtDesc(userId)) {
                latestByPurpose.putIfAbsent(rec.getPurpose(), rec);
            }

            List<ConsentSnapshot> allConsents = new ArrayList<>();
            for (ConsentPurpose purpose : ConsentPurpose.values()) {
                ConsentRecordEntity rec = latestByPurpose.get(purpose);
                if (rec == null) {
                    allConsents.add(new ConsentSnapshot(purpose.name(), "MISSING", "", ""));
                } else {
                    allConsents.add(new ConsentSnapshot(
                            purpose.name(),
                            rec.getStatus() == ConsentStatus.ACTIVE ? "ACTIVE" : "WITHDRAWN",
                            rec.getTextVersion(),
                            rec.getGrantedAt().toString()
                    ));
                }
            }

            base = new IdentityContextSnapshot(
                    user.getStatus().name(),
                    user.getRoles().stream().map(Enum::name).sorted().toList(),
                    user.getKycTier().name(),
                    allConsents,
                    user.getLockedUntil() != null ? user.getLockedUntil().toString() : "",
                    now.toString()
            );
            cache.put(userId, new CachedContext(base, now.plusSeconds(CACHE_TTL_SECONDS)));
        }

        if (requestedPurposes == null || requestedPurposes.isEmpty()) {
            return base;
        }
        Set<String> filter = new HashSet<>();
        requestedPurposes.forEach(p -> filter.add(p.toUpperCase()));
        List<ConsentSnapshot> filtered = base.consents().stream()
                .filter(c -> filter.contains(c.purpose()))
                .toList();
        return new IdentityContextSnapshot(
                base.accountStatus(), base.roles(), base.kycTier(), filtered, base.lockedUntil(), base.asOf());
    }

    public void evict(UUID userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    private record CachedContext(IdentityContextSnapshot snapshot, Instant expiresAt) {}

    public record ConsentSnapshot(String purpose, String status, String textVersion, String grantedAt) {}

    public record IdentityContextSnapshot(
            String accountStatus,
            List<String> roles,
            String kycTier,
            List<ConsentSnapshot> consents,
            String lockedUntil,
            String asOf) {}
}
