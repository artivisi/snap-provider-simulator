package com.artivisi.snapsimulator.security;

import com.artivisi.snapsimulator.repository.PartnerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Locale;

public class PartnerUserDetailsService implements UserDetailsService {

    private final PartnerRepository partners;

    public PartnerUserDetailsService(PartnerRepository partners) {
        this.partners = partners;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return partners.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(p -> new PartnerPrincipal(p.getId(), p.getEmail(), p.getPasswordHash(), p.isEnabled()))
                .orElseThrow(() -> new UsernameNotFoundException("unknown partner"));
    }
}
