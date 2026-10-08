package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.SignupRequest;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.exception.NotFoundException;
import com.artivisi.snapsimulator.repository.PartnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Partner accounts; bank registrations are in {@link ConnectionService}. */
@Service
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partners;
    private final PasswordEncoder passwordEncoder;

    public PartnerService(PartnerRepository partners, PasswordEncoder passwordEncoder) {
        this.partners = partners;
        this.passwordEncoder = passwordEncoder;
    }

    public Partner get(UUID id) {
        return partners.findById(id).orElseThrow(() -> new NotFoundException("partner " + id + " not found"));
    }

    public List<Partner> listAll() {
        return partners.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    @SpecRef("sim.portal.signup")
    public Partner signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (partners.existsByEmail(email)) {
            throw new BusinessException("email", "Email is already registered");
        }
        Partner p = new Partner();
        p.setEmail(email);
        p.setPasswordHash(passwordEncoder.encode(request.password()));
        p.setEnabled(true);
        return partners.save(p);
    }

    @Transactional
    @SpecRef("sim.admin.partners")
    public void setEnabled(UUID partnerId, boolean enabled) {
        get(partnerId).setEnabled(enabled);
    }

    @Transactional
    @SpecRef("sim.admin.partners")
    public void delete(UUID partnerId) {
        partners.delete(get(partnerId));
    }
}
