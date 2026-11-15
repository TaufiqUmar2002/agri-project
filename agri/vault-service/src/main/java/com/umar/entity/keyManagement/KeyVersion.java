package com.umar.entity.keyManagement;

import com.umar.enums.vault.LEVEL;
import com.umar.enums.vault.State;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "KEY_VERSION")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class KeyVersion {

    @Id
    private UUID id;

    @Column(name="LEVEL")
    @Enumerated(EnumType.STRING)
    private LEVEL level;

    @Column(name = "VERSION")
    private String version;

    @Column(name = "STATE")
    private State state;

    @Column(name = "HSM_REF")
    private String hsmRef;
}
