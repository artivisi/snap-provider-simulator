package com.artivisi.snapsimulator.controller.api;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.EndpointRequest;
import com.artivisi.snapsimulator.dto.GeneratedKey;
import com.artivisi.snapsimulator.dto.IssuedCredentials;
import com.artivisi.snapsimulator.dto.PartnerView;
import com.artivisi.snapsimulator.dto.PublicKeyRequest;
import com.artivisi.snapsimulator.dto.SettingsRequest;
import com.artivisi.snapsimulator.dto.SignupRequest;
import com.artivisi.snapsimulator.security.PartnerPrincipal;
import com.artivisi.snapsimulator.dto.BillerPaymentRequest;
import com.artivisi.snapsimulator.dto.ConnectionTestResult;
import com.artivisi.snapsimulator.dto.ExchangeView;
import com.artivisi.snapsimulator.dto.PaymentResult;
import com.artivisi.snapsimulator.dto.PaymentView;
import com.artivisi.snapsimulator.dto.VaPayRequest;
import com.artivisi.snapsimulator.dto.VirtualAccountView;
import com.artivisi.snapsimulator.service.ConnectionTestService;
import com.artivisi.snapsimulator.service.PaymentService;
import com.artivisi.snapsimulator.service.VirtualAccountService;
import com.artivisi.snapsimulator.service.ExchangeLogService;
import com.artivisi.snapsimulator.service.PartnerDataService;
import com.artivisi.snapsimulator.service.PartnerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** JSON counterpart of the portal pages, for scripts and tests. */
@RestController
@RequestMapping("/portal/api")
public class PortalApiController {

    private final PartnerService partners;
    private final PartnerDataService partnerData;
    private final ExchangeLogService exchangeLog;
    private final ConnectionTestService connectionTest;
    private final PaymentService payments;
    private final VirtualAccountService accounts;

    public PortalApiController(PartnerService partners, PartnerDataService partnerData, ExchangeLogService exchangeLog,
            ConnectionTestService connectionTest, PaymentService payments, VirtualAccountService accounts) {
        this.partners = partners;
        this.partnerData = partnerData;
        this.exchangeLog = exchangeLog;
        this.connectionTest = connectionTest;
        this.payments = payments;
        this.accounts = accounts;
    }

    @SpecRef("sim.portal.signup")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedCredentials signup(@Valid @RequestBody SignupRequest request) {
        return partners.signup(request);
    }

    @SpecRef("sim.portal.checklist")
    @GetMapping("/me")
    public PartnerView me(@AuthenticationPrincipal PartnerPrincipal principal) {
        return PartnerView.of(partners.get(principal.partnerId()));
    }

    @SpecRef("sim.portal.credentials")
    @PostMapping("/credentials/secret")
    public IssuedCredentials regenerateSecret(@AuthenticationPrincipal PartnerPrincipal principal) {
        return partners.regenerateSecret(principal.partnerId());
    }

    @SpecRef("sim.portal.key-upload")
    @PutMapping("/key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadKey(@AuthenticationPrincipal PartnerPrincipal principal, @Valid @RequestBody PublicKeyRequest request) {
        partners.registerPublicKey(principal.partnerId(), request.publicKeyPem());
    }

    @SpecRef("sim.portal.key-generate")
    @PostMapping("/key/generate")
    public GeneratedKey generateKey(@AuthenticationPrincipal PartnerPrincipal principal) {
        return partners.generateKey(principal.partnerId());
    }

    @SpecRef("sim.portal.settings")
    @PutMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void settings(@AuthenticationPrincipal PartnerPrincipal principal, @Valid @RequestBody SettingsRequest request) {
        partners.updateSettings(principal.partnerId(), request);
    }

    @SpecRef("sim.portal.endpoint")
    @PutMapping("/endpoint")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endpoint(@AuthenticationPrincipal PartnerPrincipal principal, @Valid @RequestBody EndpointRequest request) {
        partners.updateEndpoint(principal.partnerId(), request);
    }

    @SpecRef("sim.portal.endpoint")
    @DeleteMapping("/endpoint")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearEndpoint(@AuthenticationPrincipal PartnerPrincipal principal) {
        partners.clearEndpoint(principal.partnerId());
    }

    @SpecRef("sim.portal.reset")
    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@AuthenticationPrincipal PartnerPrincipal principal) {
        partnerData.reset(principal.partnerId());
    }

    /** Newest first. */
    @SpecRef("sim.exchange-log")
    @GetMapping("/exchanges")
    public List<ExchangeView> exchanges(@AuthenticationPrincipal PartnerPrincipal principal,
            @RequestParam("limit") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(500) int limit) {
        return exchangeLog.recent(principal.partnerId(), limit).stream().map(ExchangeView::of).toList();
    }

    @SpecRef("sim.portal.endpoint")
    @PostMapping("/endpoint/test")
    public ConnectionTestResult testConnection(@AuthenticationPrincipal PartnerPrincipal principal) {
        return connectionTest.test(principal.partnerId());
    }

    @GetMapping("/vas")
    public List<VirtualAccountView> virtualAccounts(@AuthenticationPrincipal PartnerPrincipal principal) {
        return accounts.list(principal.partnerId()).stream().map(VirtualAccountView::of).toList();
    }

    @SpecRef("sim.va.pay")
    @PostMapping("/va-payments")
    public PaymentResult payVa(@AuthenticationPrincipal PartnerPrincipal principal, @Valid @RequestBody VaPayRequest request) {
        return payments.payBankHosted(principal.partnerId(), request);
    }

    @SpecRef("sim.biller-payment.trigger")
    @PostMapping("/biller-payments")
    public PaymentResult billerPayment(@AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @RequestBody BillerPaymentRequest request) {
        return payments.payBillerHosted(principal.partnerId(), request);
    }

    @SpecRef("sim.biller-payment.resend")
    @PostMapping("/biller-payments/{id}/resend")
    public PaymentResult resend(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        return payments.resend(principal.partnerId(), id);
    }

    @GetMapping("/payments")
    public List<PaymentView> payments(@AuthenticationPrincipal PartnerPrincipal principal) {
        return payments.list(principal.partnerId()).stream().map(PaymentView::of).toList();
    }
}
