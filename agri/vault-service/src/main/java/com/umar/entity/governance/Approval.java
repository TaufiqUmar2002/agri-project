package com.umar.entity.governance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "APPROVAL")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Approval {

    @Id
    private UUID id;

    private String  type;

    private String subjectId;

    private String subjectHash;

    private String requiredRoles;

    @ElementCollection
    private List<String> decisions;
}
