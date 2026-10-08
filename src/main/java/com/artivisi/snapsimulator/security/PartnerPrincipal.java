package com.artivisi.snapsimulator.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.Serial;
import java.util.List;
import java.util.UUID;

/** A logged-in partner; carries the partner id for scoping every query. */
public class PartnerPrincipal extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID partnerId;

    public PartnerPrincipal(UUID partnerId, String email, String passwordHash, boolean enabled) {
        super(email, passwordHash, enabled, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_PARTNER")));
        this.partnerId = partnerId;
    }

    public UUID partnerId() {
        return partnerId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PartnerPrincipal p && super.equals(p) && partnerId.equals(p.partnerId);
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 31 + partnerId.hashCode();
    }
}
