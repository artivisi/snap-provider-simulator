package com.artivisi.snapsimulator.entity;

import com.artivisi.snapsimulator.enums.InjectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "injection_rule")
public class InjectionRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    /** A SnapService name for inbound rules, an OutboundTarget name for outbound ones. */
    private String target;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type")
    private InjectionType type;
    private Long delayMs;
    private Integer httpStatus;
    private int remaining;
}
