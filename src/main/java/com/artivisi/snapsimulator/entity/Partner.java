package com.artivisi.snapsimulator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A partner app's portal account; its bank registrations are {@link BankConnection}s. */
@Getter
@Setter
@Entity
@Table(name = "partner")
public class Partner extends BaseEntity {

    private String email;
    private String passwordHash;
    private boolean enabled;
}
