package com.umar.riskpool.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RiskPoolExternalClients {
    @Getter @Setter
    private volatile boolean vaultAvailable = true;
    @Getter @Setter
    private volatile boolean cashReconciled = true;
    @Getter @Setter
    private volatile boolean preJournalHold = false;

    private final Set<String> registeredInsurers = ConcurrentHashMap.newKeySet();
    {
        registeredInsurers.addAll(List.of("ins_kenya_mutual", "ins_east_africa_re", "ins_default"));
    }

    public void registerInsurer(String ref) {
        registeredInsurers.add(ref);
    }

    public boolean isInsurerRegistered(String ref) {
        return registeredInsurers.contains(ref);
    }

    public boolean verifyVaultDocuments(List<String> tokens) {
        return vaultAvailable;
    }
}
