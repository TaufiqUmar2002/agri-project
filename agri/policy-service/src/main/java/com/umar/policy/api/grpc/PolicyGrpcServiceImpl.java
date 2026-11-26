package com.umar.policy.api.grpc;

import com.umar.enums.policy.PolicyStatus;
import com.umar.payload.request.policy.CreateProductRequest;
import com.umar.policy.entity.PolicyEntity;
import com.umar.policy.entity.ProductVersionEntity;
import com.umar.policy.grpc.*;
import com.umar.policy.repository.PolicyRepository;
import com.umar.policy.repository.ProductVersionRepository;
import com.umar.policy.service.ProductGovernanceService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PolicyGrpcServiceImpl extends PolicyServiceGrpc.PolicyServiceImplBase {

    private final PolicyRepository policyRepository;
    private final ProductVersionRepository productRepository;
    private final ProductGovernanceService productService;

    @Override
    public void getPolicyTerms(GetPolicyTermsRequest req, StreamObserver<GetPolicyTermsResponse> responseObserver) {
        boolean hasPolicies = req.getPolicyIdsCount() > 0;
        boolean hasPlots = req.getPlotIdsCount() > 0;
        if (hasPolicies == hasPlots
                || req.getPolicyIdsCount() > 500
                || req.getPlotIdsCount() > 500) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Supply between 1 and 500 of either policy_ids or plot_ids")
                    .asRuntimeException());
            return;
        }

        Instant asOf;
        try {
            asOf = req.getAsOf().isBlank() ? Instant.now() : Instant.parse(req.getAsOf());
        } catch (Exception e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid as_of timestamp").asRuntimeException());
            return;
        }

        List<PolicyEntity> found = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        if (hasPolicies) {
            for (String id : req.getPolicyIdsList()) {
                policyRepository.findById(id).ifPresentOrElse(found::add, () -> notFound.add(id));
            }
        } else {
            for (String plotId : req.getPlotIdsList()) {
                List<PolicyEntity> byPlot = policyRepository.findByPlotId(plotId).stream()
                        .filter(p -> p.getCoverageStart() == null || !asOf.isBefore(p.getCoverageStart()))
                        .filter(p -> p.getCoverageEnd() == null || !asOf.isAfter(p.getCoverageEnd()))
                        .toList();
                if (byPlot.isEmpty()) notFound.add(plotId);
                else found.addAll(byPlot);
            }
        }

        GetPolicyTermsResponse.Builder resp = GetPolicyTermsResponse.newBuilder()
                .setAsOf(asOf.toString())
                .addAllNotFoundIds(notFound);

        for (PolicyEntity p : found) {
            PolicyTermsEntry.Builder entry = PolicyTermsEntry.newBuilder()
                    .setPolicyId(p.getPolicyId())
                    .setPolicyNumber(p.getPolicyNumber())
                    .setFarmerId(p.getFarmerId().toString())
                    .setPlotId(p.getPlotId())
                    .setStatus(p.getStatus().name())
                    .setProductId(p.getProductId())
                    .setProductVersion(p.getProductVersion())
                    .setDefinitionHash(p.getDefinitionHash())
                    .setTermsHash(p.getTermsHash())
                    .setCurrency(p.getCurrency())
                    .setSumInsuredMinor(p.getSumInsuredMinor())
                    .setCoverageStart(p.getCoverageStart() != null ? p.getCoverageStart().toString() : "")
                    .setCoverageEnd(p.getCoverageEnd() != null ? p.getCoverageEnd().toString() : "")
                    .setPremiumConfirmed("PAID".equals(p.getPremiumStatus()))
                    .setPayoutState(p.getPayoutState().name())
                    .setPaidToDateMinor(p.getPaidToDateMinor())
                    .setRemainingMinor(Math.max(0L, p.getSumInsuredMinor() - p.getPaidToDateMinor()));

            if (req.getIncludeProductTerms()) {
                productRepository.findByProductIdAndVersionNum(p.getProductId(), p.getProductVersion())
                        .ifPresent(pv -> entry.setProductTerms(toProductTerms(productService.readDefinition(pv.getDefinitionJson()))));
            }
            resp.addPolicies(entry.build());
        }
        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }

    @Override
    public void listFarmerPolicies(ListFarmerPoliciesRequest req, StreamObserver<ListFarmerPoliciesResponse> responseObserver) {
        UUID farmerId;
        try {
            farmerId = UUID.fromString(req.getFarmerId());
        } catch (Exception e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid farmer_id UUID").asRuntimeException());
            return;
        }
        Set<String> statusFilter = new HashSet<>(req.getStatusesList());
        Instant now = Instant.now();
        Instant endCutoff = req.getEndingWithinDays() > 0 ? now.plus(req.getEndingWithinDays(), ChronoUnit.DAYS) : null;

        List<PolicyEntity> policies = policyRepository.findByFarmerId(farmerId).stream()
                .filter(p -> statusFilter.isEmpty() || statusFilter.contains(p.getStatus().name()))
                .filter(p -> endCutoff == null || (p.getCoverageEnd() != null && !p.getCoverageEnd().isAfter(endCutoff)))
                .limit(50)
                .toList();

        ListFarmerPoliciesResponse.Builder resp = ListFarmerPoliciesResponse.newBuilder()
                .setAsOf(now.toString());

        for (PolicyEntity p : policies) {
            String prodName = productRepository.findByProductIdAndVersionNum(p.getProductId(), p.getProductVersion())
                    .map(pv -> productService.readDefinition(pv.getDefinitionJson()).getName().getOrDefault("en", pv.getProductCode()))
                    .orElse(p.getProductId());
            boolean renewalDue = p.getStatus() == PolicyStatus.ACTIVE && p.getCoverageEnd() != null
                    && !p.getCoverageEnd().isAfter(now.plus(30, ChronoUnit.DAYS));

            resp.addPolicies(FarmerPolicySummary.newBuilder()
                    .setPolicyId(p.getPolicyId())
                    .setPolicyNumber(p.getPolicyNumber())
                    .setStatus(p.getStatus().name())
                    .setProductId(p.getProductId())
                    .setProductName(prodName)
                    .setPlotId(p.getPlotId())
                    .setCoverageStart(p.getCoverageStart() != null ? p.getCoverageStart().toString() : "")
                    .setCoverageEnd(p.getCoverageEnd() != null ? p.getCoverageEnd().toString() : "")
                    .setPremiumStatus(p.getPremiumStatus())
                    .setPayoutState(p.getPayoutState().name())
                    .setRenewalDue(renewalDue)
                    .build());
        }
        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }

    private ProductTerms toProductTerms(CreateProductRequest def) {
        ProductTerms.Builder pt = ProductTerms.newBuilder()
                .setIndexRef(def.getIndex().getType() + ":" + def.getIndex().getSource().name());
        for (var ph : def.getIndex().getPhases()) {
            pt.addPhases(PhaseTerm.newBuilder()
                    .setName(ph.getName())
                    .setStartOffsetDays(ph.getStartOffsetDays())
                    .setEndOffsetDays(ph.getEndOffsetDays())
                    .setWeight(ph.getWeight())
                    .build());
        }
        for (var tier : def.getTrigger().getTiers()) {
            pt.addTiers(TierTerm.newBuilder()
                    .setOperator(tier.getOperator())
                    .setThresholdValue(tier.getThresholdValue())
                    .setPayoutPercent(tier.getPayoutPercent())
                    .build());
        }
        CorroborationRule.Builder cb = CorroborationRule.newBuilder()
                .setRequiredSources(def.getTrigger().getCorroboration().getRequiredSources());
        if (def.getTrigger().getCorroboration().getAllowedSources() != null) {
            cb.addAllAllowedSources(def.getTrigger().getCorroboration().getAllowedSources());
        }
        pt.setCorroboration(cb.build());
        return pt.build();
    }
}
