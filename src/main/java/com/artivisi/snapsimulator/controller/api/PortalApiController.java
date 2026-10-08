package com.artivisi.snapsimulator.controller.api;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.AccountView;
import com.artivisi.snapsimulator.dto.BillerPaymentRequest;
import com.artivisi.snapsimulator.dto.ConnectionRequest;
import com.artivisi.snapsimulator.dto.ConnectionTestResult;
import com.artivisi.snapsimulator.dto.ConnectionView;
import com.artivisi.snapsimulator.dto.EndpointRequest;
import com.artivisi.snapsimulator.dto.ExchangeView;
import com.artivisi.snapsimulator.dto.GeneratedKey;
import com.artivisi.snapsimulator.dto.InjectionRuleRequest;
import com.artivisi.snapsimulator.dto.InjectionRuleView;
import com.artivisi.snapsimulator.dto.IssuedCredentials;
import com.artivisi.snapsimulator.dto.PaymentResult;
import com.artivisi.snapsimulator.dto.PaymentView;
import com.artivisi.snapsimulator.dto.PublicKeyRequest;
import com.artivisi.snapsimulator.dto.SeedRequest;
import com.artivisi.snapsimulator.dto.SeedResult;
import com.artivisi.snapsimulator.dto.SettingsRequest;
import com.artivisi.snapsimulator.dto.SignupRequest;
import com.artivisi.snapsimulator.dto.VaPayRequest;
import com.artivisi.snapsimulator.dto.VirtualAccountView;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.security.PartnerPrincipal;
import com.artivisi.snapsimulator.service.ConnectionService;
import com.artivisi.snapsimulator.service.ConnectionTestService;
import com.artivisi.snapsimulator.service.ExchangeLogService;
import com.artivisi.snapsimulator.service.InjectionService;
import com.artivisi.snapsimulator.service.PartnerDataService;
import com.artivisi.snapsimulator.service.PartnerService;
import com.artivisi.snapsimulator.service.PaymentService;
import com.artivisi.snapsimulator.service.ReconciliationSeeder;
import com.artivisi.snapsimulator.service.StatementService;
import com.artivisi.snapsimulator.service.VirtualAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** JSON counterpart of the portal pages, for scripts and tests. Connection paths: /connections/{id}/... */
@RestController
@RequestMapping("/portal/api")
public class PortalApiController {

    private final PartnerService partners;
    private final ConnectionService connections;
    private final PartnerDataService partnerData;
    private final ExchangeLogService exchangeLog;
    private final ConnectionTestService connectionTest;
    private final PaymentService payments;
    private final VirtualAccountService accounts;
    private final InjectionService injections;
    private final StatementService statements;
    private final ReconciliationSeeder seeder;

    public PortalApiController(PartnerService partners, ConnectionService connections, PartnerDataService partnerData,
            ExchangeLogService exchangeLog, ConnectionTestService connectionTest, PaymentService payments,
            VirtualAccountService accounts, InjectionService injections, StatementService statements,
            ReconciliationSeeder seeder) {
        this.partners = partners;
        this.connections = connections;
        this.partnerData = partnerData;
        this.exchangeLog = exchangeLog;
        this.connectionTest = connectionTest;
        this.payments = payments;
        this.accounts = accounts;
        this.injections = injections;
        this.statements = statements;
        this.seeder = seeder;
    }

    private BankConnection owned(PartnerPrincipal principal, UUID id) {
        return connections.owned(principal.partnerId(), id);
    }

    @SpecRef("sim.portal.signup")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView signup(@Valid @RequestBody SignupRequest request) {
        return connections.account(partners.signup(request).getId());
    }

    @SpecRef("sim.portal.checklist")
    @GetMapping("/me")
    public AccountView me(@AuthenticationPrincipal PartnerPrincipal principal) {
        return connections.account(principal.partnerId());
    }

    @SpecRef("sim.portal.connection")
    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedCredentials addConnection(@AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @RequestBody ConnectionRequest request) {
        return connections.create(principal.partnerId(), request);
    }

    @SpecRef("sim.portal.checklist")
    @GetMapping("/connections/{id}")
    public ConnectionView connection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        return connections.view(owned(principal, id));
    }

    @SpecRef("sim.portal.credentials")
    @PostMapping("/connections/{id}/credentials/secret")
    public IssuedCredentials regenerateSecret(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        return connections.regenerateSecret(principal.partnerId(), id);
    }

    @SpecRef("sim.portal.key-upload")
    @PutMapping("/connections/{id}/key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadKey(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody PublicKeyRequest request) {
        connections.registerPublicKey(principal.partnerId(), id, request.publicKeyPem());
    }

    @SpecRef("sim.portal.key-generate")
    @PostMapping("/connections/{id}/key/generate")
    public GeneratedKey generateKey(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        return connections.generateKey(principal.partnerId(), id);
    }

    @SpecRef("sim.portal.settings")
    @PutMapping("/connections/{id}/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void settings(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SettingsRequest request) {
        connections.updateSettings(principal.partnerId(), id, request);
    }

    @SpecRef("sim.portal.endpoint")
    @PutMapping("/connections/{id}/endpoint")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endpoint(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody EndpointRequest request) {
        connections.updateEndpoint(principal.partnerId(), id, request);
    }

    @SpecRef("sim.portal.endpoint")
    @DeleteMapping("/connections/{id}/endpoint")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearEndpoint(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        connections.clearEndpoint(principal.partnerId(), id);
    }

    @SpecRef("sim.portal.endpoint")
    @PostMapping("/connections/{id}/endpoint/test")
    public ConnectionTestResult testConnection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        return connectionTest.test(owned(principal, id));
    }

    @SpecRef("sim.portal.reset")
    @PostMapping("/connections/{id}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        partnerData.reset(owned(principal, id).getId());
    }

    /** Newest first. */
    @SpecRef("sim.exchange-log")
    @GetMapping("/connections/{id}/exchanges")
    public List<ExchangeView> exchanges(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @RequestParam("limit") @Min(1) @Max(500) int limit) {
        return exchangeLog.recent(owned(principal, id).getId(), limit).stream().map(ExchangeView::of).toList();
    }

    @GetMapping("/connections/{id}/vas")
    public List<VirtualAccountView> virtualAccounts(@AuthenticationPrincipal PartnerPrincipal principal,
            @PathVariable UUID id) {
        return accounts.list(owned(principal, id).getId()).stream().map(VirtualAccountView::of).toList();
    }

    @SpecRef("sim.va.pay")
    @PostMapping("/connections/{id}/va-payments")
    public PaymentResult payVa(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody VaPayRequest request) {
        return payments.payBankHosted(owned(principal, id), request);
    }

    @SpecRef("sim.biller-payment.trigger")
    @PostMapping("/connections/{id}/biller-payments")
    public PaymentResult billerPayment(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody BillerPaymentRequest request) {
        return payments.payBillerHosted(owned(principal, id), request);
    }

    @SpecRef("sim.biller-payment.resend")
    @PostMapping("/connections/{id}/payments/{paymentId}/resend")
    public PaymentResult resend(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID paymentId) {
        return payments.resend(owned(principal, id), paymentId);
    }

    @GetMapping("/connections/{id}/payments")
    public List<PaymentView> payments(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        return payments.list(owned(principal, id).getId()).stream().map(PaymentView::of).toList();
    }

    @SpecRef("sim.error-injection")
    @GetMapping("/connections/{id}/injections")
    public List<InjectionRuleView> injections(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        return injections.list(owned(principal, id).getId()).stream().map(InjectionRuleView::of).toList();
    }

    @SpecRef("sim.error-injection")
    @PostMapping("/connections/{id}/injections")
    @ResponseStatus(HttpStatus.CREATED)
    public InjectionRuleView addInjection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody InjectionRuleRequest request) {
        return InjectionRuleView.of(injections.add(owned(principal, id), request));
    }

    @SpecRef("sim.error-injection")
    @DeleteMapping("/connections/{id}/injections/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInjection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID ruleId) {
        injections.delete(owned(principal, id).getId(), ruleId);
    }

    @SpecRef("sim.statement-csv")
    @GetMapping("/connections/{id}/statements/{day}.csv")
    public ResponseEntity<String> statement(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        BankConnection connection = owned(principal, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statement-" + connection.getBank().slug() + "-" + day + ".csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(statements.csv(connection.getId(), day));
    }

    @SpecRef("sim.reconciliation-seeder")
    @PostMapping("/connections/{id}/reconciliation-seed")
    public List<SeedResult> seed(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SeedRequest request) {
        return seeder.seed(owned(principal, id), request);
    }
}
