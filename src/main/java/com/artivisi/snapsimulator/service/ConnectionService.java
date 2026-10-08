package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.bank.BankProfile;
import com.artivisi.snapsimulator.bank.BankProfiles;
import com.artivisi.snapsimulator.dto.AccountView;
import com.artivisi.snapsimulator.dto.ConnectionRequest;
import com.artivisi.snapsimulator.dto.ConnectionView;
import com.artivisi.snapsimulator.dto.EndpointRequest;
import com.artivisi.snapsimulator.dto.GeneratedKey;
import com.artivisi.snapsimulator.dto.IssuedCredentials;
import com.artivisi.snapsimulator.dto.SettingsRequest;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.exception.NotFoundException;
import com.artivisi.snapsimulator.repository.BankConnectionRepository;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.util.Randoms;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** A partner's bank connections: credentials, key, endpoint and settings per bank. */
@Service
@Transactional(readOnly = true)
public class ConnectionService {

    private static final int CLIENT_ID_LENGTH = 32;
    private static final int CLIENT_SECRET_LENGTH = 48;

    private final BankConnectionRepository connections;
    private final PartnerService partners;
    private final ChecklistService checklist;
    private final BankProfiles profiles;
    private final Clock clock;

    public ConnectionService(BankConnectionRepository connections, PartnerService partners, ChecklistService checklist,
            BankProfiles profiles, Clock clock) {
        this.connections = connections;
        this.partners = partners;
        this.checklist = checklist;
        this.profiles = profiles;
        this.clock = clock;
    }

    public BankConnection get(UUID connectionId) {
        return connections.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("connection " + connectionId + " not found"));
    }

    /** The connection, if it belongs to the partner; NotFound otherwise. */
    public BankConnection owned(UUID partnerId, UUID connectionId) {
        return connections.findByIdAndPartnerId(connectionId, partnerId)
                .orElseThrow(() -> new NotFoundException("connection " + connectionId + " not found"));
    }

    public List<BankConnection> list(UUID partnerId) {
        return connections.findByPartnerIdOrderByCreatedAt(partnerId);
    }

    public ConnectionView view(BankConnection c) {
        BankProfile profile = profiles.of(c.getBank());
        return ConnectionView.of(c, profile.checklist(), profile.bankHostedVa());
    }

    public AccountView account(UUID partnerId) {
        Partner partner = partners.get(partnerId);
        return new AccountView(partner.getEmail(), list(partnerId).stream().map(this::view).toList());
    }

    /** Issues the VA prefix, client id and secret for one bank (A17, A48). */
    @Transactional
    @SpecRef("sim.portal.connection")
    @SpecRef("sim.portal.credentials")
    @SpecRef("bri.va.number-layout")
    @SpecRef("bca.va.number-layout")
    public IssuedCredentials create(UUID partnerId, ConnectionRequest request) {
        BankConnection c = new BankConnection();
        c.setPartner(partners.get(partnerId));
        c.setBank(request.bank());
        c.setPartnerServiceId(String.format("%8d", connections.nextPartnerServiceNumber()));
        c.setClientId(Randoms.alphanumeric(CLIENT_ID_LENGTH));
        c.setClientSecret(Randoms.alphanumeric(CLIENT_SECRET_LENGTH));
        c.setTokenTtlSeconds(request.tokenTtlSeconds());
        c.setDiagnosticMode(request.diagnosticMode());
        connections.save(c);
        return issued(c);
    }

    /** Replaces the client secret; the old one stops working immediately. */
    @Transactional
    @SpecRef("sim.portal.credentials")
    public IssuedCredentials regenerateSecret(UUID partnerId, UUID connectionId) {
        BankConnection c = owned(partnerId, connectionId);
        c.setClientSecret(Randoms.alphanumeric(CLIENT_SECRET_LENGTH));
        return issued(c);
    }

    private static IssuedCredentials issued(BankConnection c) {
        return new IssuedCredentials(c.getId(), c.getBank(), c.getPartnerServiceId(), c.getClientId(), c.getClientSecret());
    }

    @Transactional
    @SpecRef("sim.portal.key-upload")
    public void registerPublicKey(UUID partnerId, UUID connectionId, String pem) {
        try {
            storeKey(owned(partnerId, connectionId), PemKeys.toPem(PemKeys.parsePublicKey(pem)));
        } catch (PemKeys.InvalidKeyException e) {
            throw new BusinessException("publicKeyPem", e.getMessage());
        }
    }

    /** Generates a pair, stores the public key and returns the private key once. */
    @Transactional
    @SpecRef("sim.portal.key-generate")
    public GeneratedKey generateKey(UUID partnerId, UUID connectionId) {
        BankConnection c = owned(partnerId, connectionId);
        KeyPair pair = PemKeys.generateRsa();
        String publicPem = PemKeys.toPem(pair.getPublic());
        storeKey(c, publicPem);
        return new GeneratedKey(publicPem, PemKeys.toPem(pair.getPrivate()));
    }

    private void storeKey(BankConnection c, String publicPem) {
        c.setPublicKeyPem(publicPem);
        connections.flush();
        checklist.stamp(c.getId(), ChecklistItem.KEY_REGISTERED, clock.instant());
    }

    @Transactional
    @SpecRef("sim.portal.settings")
    public void updateSettings(UUID partnerId, UUID connectionId, SettingsRequest request) {
        BankConnection c = owned(partnerId, connectionId);
        c.setTokenTtlSeconds(request.tokenTtlSeconds());
        c.setDiagnosticMode(request.diagnosticMode());
    }

    @Transactional
    @SpecRef("sim.portal.endpoint")
    public void updateEndpoint(UUID partnerId, UUID connectionId, EndpointRequest request) {
        BankConnection c = owned(partnerId, connectionId);
        c.setEndpointBaseUrl(request.baseUrl().replaceAll("/+$", ""));
        c.setEndpointClientId(request.clientId());
        c.setEndpointClientSecret(request.clientSecret());
    }

    @Transactional
    @SpecRef("sim.portal.endpoint")
    public void clearEndpoint(UUID partnerId, UUID connectionId) {
        BankConnection c = owned(partnerId, connectionId);
        c.setEndpointBaseUrl(null);
        c.setEndpointClientId(null);
        c.setEndpointClientSecret(null);
    }
}
