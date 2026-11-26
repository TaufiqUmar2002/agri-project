package com.umar.riskpool.domain;

import com.umar.enums.riskpool.PeriodStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "pool_periods")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PoolPeriodEntity {
    @Id @Column(name = "period_key") private String periodKey;
    @Column(name = "pool_id", nullable = false) private String poolId;
    @Column(nullable = false) private String period;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PeriodStatus status;
    @Column(name = "earned_minor", nullable = false) private long earnedMinor;
    @Column(name = "ceded_earned_minor", nullable = false) private long cededEarnedMinor;
    @Column(name = "trial_balance_hash") private String trialBalanceHash;
    @Column(name = "close_package_token") private String closePackageToken;
}
