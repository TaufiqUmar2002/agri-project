package com.umar.riskpool.grpc;

import com.umar.riskpool.config.RiskPoolProperties;
import com.umar.riskpool.domain.CapacityReservationEntity;
import com.umar.riskpool.domain.PolicyPremiumRegisterEntity;
import com.umar.riskpool.domain.RiskPoolEntity;
import com.umar.riskpool.grpc.proto.*;
import com.umar.riskpool.repository.CapacityReservationRepository;
import com.umar.riskpool.repository.PolicyPremiumRegisterRepository;
import com.umar.riskpool.repository.RiskPoolRepository;
import com.umar.riskpool.service.RiskPoolLedgerService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskPoolGrpcServiceImpl extends PoolServiceGrpc.PoolServiceImplBase {

    private final RiskPoolRepository poolRepository;
    private final PolicyPremiumRegisterRepository registerRepository;
    private final CapacityReservationRepository capacityRepository;
    private final RiskPoolLedgerService ledgerService;
    private final RiskPoolProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @SuppressWarnings("unchecked")
    public void checkCapacity(CheckCapacityRequest req, StreamObserver<CheckCapacityResponse> observer) {
        String caller = RiskPoolGrpcAuthInterceptor.CALLER_ID.get();
        List<String> allowed = Arrays.stream(properties.getGrpc().getAllowedCallers().split(","))
                .map(String::trim).map(String::toLowerCase).toList();
        if (caller == null || !allowed.contains(caller)) {
            observer.onError(Status.PERMISSION_DENIED.withDescription("Caller not allowed").asRuntimeException());
            return;
        }
        if (req.getPolicyId().isBlank() || req.getProductId().isBlank() || req.getSumInsuredMinor() <= 0) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("policy_id, product_id and positive sum_insured_minor required").asRuntimeException());
            return;
        }

        Optional<String> poolIdOpt = ledgerService.resolvePoolForRisk(req.getProductId(), req.getRegionCode(), req.getPeril());
        if (poolIdOpt.isEmpty()) {
            observer.onError(Status.NOT_FOUND.withDescription("No active pool for this product and region").asRuntimeException());
            return;
        }

        RiskPoolEntity pool = poolRepository.findById(poolIdOpt.get()).orElseThrow();
        if (!pool.getCurrency().equals(req.getCurrency())) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Currency mismatch with pool").asRuntimeException());
            return;
        }

        Map<String, Object> limits;
        try { limits = objectMapper.readValue(pool.getLimitsJson(), Map.class); }
        catch (Exception e) { limits = Map.of(); }
        long capMinor = ((Number) limits.getOrDefault("totalExposureCapMinor", 10_000_000_000L)).longValue();

        long currentActive = registerRepository.findByPoolId(pool.getPoolId()).stream()
                .filter(PolicyPremiumRegisterEntity::isInForce)
                .mapToLong(PolicyPremiumRegisterEntity::getSumInsuredMinor).sum();
        long currentRes = capacityRepository.findByPoolIdAndStatus(pool.getPoolId(), "RESERVED").stream()
                .mapToLong(CapacityReservationEntity::getSumInsuredMinor).sum();

        long headroom = Math.max(0L, capMinor - currentActive - currentRes);
        boolean ok = (currentActive + currentRes + req.getSumInsuredMinor()) <= capMinor;

        ReservationInfo resInfo = null;
        if (ok && !"CHECK_ONLY".equalsIgnoreCase(req.getMode())) {
            int days = req.getReservationDays() > 0 ? Math.min(req.getReservationDays(), 14) : 7;
            Instant exp = Instant.now().plus(days, ChronoUnit.DAYS);
            CapacityReservationEntity cr = capacityRepository.findByPolicyId(req.getPolicyId())
                    .orElse(CapacityReservationEntity.builder()
                            .reservationId("cap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                            .policyId(req.getPolicyId()).poolId(pool.getPoolId())
                            .regionCode(req.getRegionCode()).peril(req.getPeril()).season(req.getSeason())
                            .sumInsuredMinor(req.getSumInsuredMinor()).currency(req.getCurrency())
                            .status("RESERVED").expiresAt(exp).build());
            capacityRepository.save(cr);
            resInfo = ReservationInfo.newBuilder()
                    .setReservationId(cr.getReservationId()).setExpiresAt(cr.getExpiresAt().toString()).build();
        }

        CheckCapacityResponse.Builder b = CheckCapacityResponse.newBuilder()
                .setAllowed(ok)
                .addReasons(ok ? "NONE" : "TOTAL_EXPOSURE_CAP")
                .setPoolId(pool.getPoolId())
                .setHeadroomMinor(headroom)
                .setAsOf(Instant.now().toString());
        if (resInfo != null) b.setReservation(resInfo);

        observer.onNext(b.build());
        observer.onCompleted();
    }
}
