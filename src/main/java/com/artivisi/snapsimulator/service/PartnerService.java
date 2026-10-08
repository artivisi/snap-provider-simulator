package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.EndpointRequest;
import com.artivisi.snapsimulator.dto.GeneratedKey;
import com.artivisi.snapsimulator.dto.IssuedCredentials;
import com.artivisi.snapsimulator.dto.SettingsRequest;
import com.artivisi.snapsimulator.dto.SignupRequest;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.exception.NotFoundException;
import com.artivisi.snapsimulator.repository.PartnerRepository;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.util.Randoms;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PartnerService {

    private static final int CLIENT_ID_LENGTH = 32;
    private static final int CLIENT_SECRET_LENGTH = 48;

    private final PartnerRepository partners;
    private final PasswordEncoder passwordEncoder;
    private final ChecklistService checklist;
    private final Clock clock;

    public PartnerService(PartnerRepository partners, PasswordEncoder passwordEncoder, ChecklistService checklist,
            Clock clock) {
        this.partners = partners;
        this.passwordEncoder = passwordEncoder;
        this.checklist = checklist;
        this.clock = clock;
    }

    public Partner get(UUID id) {
        return partners.findById(id).orElseThrow(() -> new NotFoundException("partner " + id + " not found"));
    }

    public Partner getByEmail(String email) {
        return partners.findByEmail(email).orElseThrow(() -> new NotFoundException("partner " + email + " not found"));
    }

    public List<Partner> listAll() {
        return partners.findAllByOrderByCreatedAtDesc();
    }

    /** Creates the account and issues the VA prefix, client id and secret. */
    @Transactional
    @SpecRef("sim.portal.signup")
    @SpecRef("bri.va.number-layout")
    public IssuedCredentials signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (partners.existsByEmail(email)) {
            throw new BusinessException("email", "Email is already registered");
        }
        Partner p = new Partner();
        p.setEmail(email);
        p.setPasswordHash(passwordEncoder.encode(request.password()));
        p.setEnabled(true);
        p.setPartnerServiceId(String.format("%8d", partners.nextPartnerServiceNumber()));
        p.setClientId(Randoms.alphanumeric(CLIENT_ID_LENGTH));
        p.setClientSecret(Randoms.alphanumeric(CLIENT_SECRET_LENGTH));
        p.setTokenTtlSeconds(request.tokenTtlSeconds());
        p.setDiagnosticMode(request.diagnosticMode());
        partners.save(p);
        return new IssuedCredentials(p.getPartnerServiceId(), p.getClientId(), p.getClientSecret());
    }

    /** Replaces the client secret; the old one stops working immediately. */
    @Transactional
    @SpecRef("sim.portal.credentials")
    public IssuedCredentials regenerateSecret(UUID partnerId) {
        Partner p = get(partnerId);
        p.setClientSecret(Randoms.alphanumeric(CLIENT_SECRET_LENGTH));
        return new IssuedCredentials(p.getPartnerServiceId(), p.getClientId(), p.getClientSecret());
    }

    @Transactional
    @SpecRef("sim.portal.key-upload")
    public void registerPublicKey(UUID partnerId, String pem) {
        try {
            String normalized = PemKeys.toPem(PemKeys.parsePublicKey(pem));
            storeKey(partnerId, normalized);
        } catch (PemKeys.InvalidKeyException e) {
            throw new BusinessException("publicKeyPem", e.getMessage());
        }
    }

    /** Generates a pair, stores the public key and returns the private key once. */
    @Transactional
    @SpecRef("sim.portal.key-generate")
    public GeneratedKey generateKey(UUID partnerId) {
        KeyPair pair = PemKeys.generateRsa();
        String publicPem = PemKeys.toPem(pair.getPublic());
        storeKey(partnerId, publicPem);
        return new GeneratedKey(publicPem, PemKeys.toPem(pair.getPrivate()));
    }

    private void storeKey(UUID partnerId, String publicPem) {
        Partner p = get(partnerId);
        p.setPublicKeyPem(publicPem);
        partners.flush();
        checklist.stamp(partnerId, ChecklistItem.KEY_REGISTERED, clock.instant());
    }

    @Transactional
    @SpecRef("sim.portal.settings")
    public void updateSettings(UUID partnerId, SettingsRequest request) {
        Partner p = get(partnerId);
        p.setTokenTtlSeconds(request.tokenTtlSeconds());
        p.setDiagnosticMode(request.diagnosticMode());
    }

    @Transactional
    @SpecRef("sim.portal.endpoint")
    public void updateEndpoint(UUID partnerId, EndpointRequest request) {
        Partner p = get(partnerId);
        p.setEndpointBaseUrl(request.baseUrl().replaceAll("/+$", ""));
        p.setEndpointClientId(request.clientId());
        p.setEndpointClientSecret(request.clientSecret());
    }

    @Transactional
    @SpecRef("sim.portal.endpoint")
    public void clearEndpoint(UUID partnerId) {
        Partner p = get(partnerId);
        p.setEndpointBaseUrl(null);
        p.setEndpointClientId(null);
        p.setEndpointClientSecret(null);
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
