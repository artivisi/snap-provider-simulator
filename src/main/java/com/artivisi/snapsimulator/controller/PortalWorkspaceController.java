package com.artivisi.snapsimulator.controller;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.bank.BankProfiles;
import com.artivisi.snapsimulator.dto.BillerPaymentRequest;
import com.artivisi.snapsimulator.dto.ExchangeView;
import com.artivisi.snapsimulator.dto.InjectionRuleRequest;
import com.artivisi.snapsimulator.dto.PaymentResult;
import com.artivisi.snapsimulator.dto.SeedRequest;
import com.artivisi.snapsimulator.dto.VaPayRequest;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.enums.InjectionType;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.security.PartnerPrincipal;
import com.artivisi.snapsimulator.service.ConnectionService;
import com.artivisi.snapsimulator.service.ConnectionTestService;
import com.artivisi.snapsimulator.service.ExchangeLogService;
import com.artivisi.snapsimulator.service.InjectionService;
import com.artivisi.snapsimulator.service.PaymentService;
import com.artivisi.snapsimulator.service.ReconciliationSeeder;
import com.artivisi.snapsimulator.service.VirtualAccountService;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pages for working with one bank connection: VAs, payments, exchange log, injection. */
@Controller
@RequestMapping("/portal/c/{id}")
public class PortalWorkspaceController {

    private static final int EXCHANGE_PAGE = 50;

    private final ConnectionService connections;
    private final VirtualAccountService accounts;
    private final PaymentService payments;
    private final ReconciliationSeeder seeder;
    private final ExchangeLogService exchangeLog;
    private final InjectionService injections;
    private final ConnectionTestService connectionTest;
    private final BankProfiles profiles;
    private final Validator validator;

    public PortalWorkspaceController(ConnectionService connections, VirtualAccountService accounts,
            PaymentService payments, ReconciliationSeeder seeder, ExchangeLogService exchangeLog,
            InjectionService injections, ConnectionTestService connectionTest, BankProfiles profiles,
            Validator validator) {
        this.connections = connections;
        this.accounts = accounts;
        this.payments = payments;
        this.seeder = seeder;
        this.exchangeLog = exchangeLog;
        this.injections = injections;
        this.connectionTest = connectionTest;
        this.profiles = profiles;
        this.validator = validator;
    }

    private BankConnection connection(PartnerPrincipal principal, UUID id, Model model) {
        BankConnection c = PortalController.connection(connections, principal, id, model);
        model.addAttribute("channels", profiles.of(c.getBank()).channels());
        return c;
    }

    private static String redirect(UUID id, String page) {
        return "redirect:/portal/c/" + id + page;
    }

    @GetMapping("/vas")
    public String virtualAccounts(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id, Model model) {
        model.addAttribute("vas", accounts.list(connection(principal, id, model).getId()));
        return "portal/vas";
    }

    @SpecRef("sim.va.pay")
    @PostMapping("/vas/pay")
    public String payVa(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @RequestParam String virtualAccountNo, @RequestParam String channelId, RedirectAttributes redirect) {
        BankConnection c = connections.owned(principal.partnerId(), id);
        run(redirect, new VaPayRequest(virtualAccountNo, channelId), r -> describe(payments.payBankHosted(c, r)));
        return redirect(id, "/vas");
    }

    @GetMapping("/payments")
    public String payments(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id, Model model) {
        model.addAttribute("payments", payments.list(connection(principal, id, model).getId()));
        model.addAttribute("today", LocalDate.now(SnapTimestamp.JAKARTA).toString());
        return "portal/payments";
    }

    @SpecRef("sim.biller-payment.trigger")
    @PostMapping("/payments/biller")
    public String billerPayment(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @RequestParam String virtualAccountNo, @RequestParam String amount, @RequestParam String channelId,
            RedirectAttributes redirect) {
        BankConnection c = connections.owned(principal.partnerId(), id);
        run(redirect, new BillerPaymentRequest(virtualAccountNo, amount, channelId),
                r -> describe(payments.payBillerHosted(c, r)));
        return redirect(id, "/payments");
    }

    @SpecRef("sim.biller-payment.resend")
    @PostMapping("/payments/{paymentId}/resend")
    public String resend(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID paymentId, RedirectAttributes redirect) {
        BankConnection c = connections.owned(principal.partnerId(), id);
        run(redirect, paymentId, p -> payments.resend(c, p).message());
        return redirect(id, "/payments");
    }

    @SpecRef("sim.reconciliation-seeder")
    @PostMapping("/payments/seed")
    public String seed(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @RequestParam String virtualAccountNos, @RequestParam String channelId, RedirectAttributes redirect) {
        BankConnection c = connections.owned(principal.partnerId(), id);
        List<String> numbers = Arrays.stream(virtualAccountNos.split("\\R")).filter(s -> !s.isBlank())
                .map(s -> s.replaceAll("\\s+$", "")).toList();
        run(redirect, new SeedRequest(numbers, channelId), r -> {
            StringBuilder sb = new StringBuilder("Seeded: ");
            seeder.seed(c, r).forEach(s -> sb.append(s.scenario()).append(' ')
                    .append(s.result().notificationStatus()).append("; "));
            return sb.toString();
        });
        return redirect(id, "/payments");
    }

    @SpecRef("sim.exchange-log")
    @GetMapping("/exchanges")
    public String exchanges(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id, Model model) {
        model.addAttribute("exchanges", exchangeLog.recent(connection(principal, id, model).getId(), EXCHANGE_PAGE)
                .stream().map(ExchangeView::of).toList());
        return "portal/exchanges";
    }

    @GetMapping("/injections")
    public String injectionRules(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            Model model) {
        BankConnection c = connection(principal, id, model);
        model.addAttribute("rules", injections.list(c.getId()));
        model.addAttribute("types", InjectionType.values());
        Map<InjectionType, List<String>> targets = new LinkedHashMap<>();
        for (InjectionType type : InjectionType.values()) {
            targets.put(type, InjectionService.validTargets(c.getBank(), type));
        }
        model.addAttribute("targets", targets);
        model.addAttribute("inboundTargets", targets.get(InjectionType.SLOW_RESPONSE));
        return "portal/injections";
    }

    @SpecRef("sim.error-injection")
    @PostMapping("/injections")
    public String addInjection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @RequestParam InjectionType type, @RequestParam String target,
            @RequestParam(required = false) Long delayMs, @RequestParam(required = false) Integer httpStatus,
            @RequestParam Integer remaining, RedirectAttributes redirect) {
        BankConnection c = connections.owned(principal.partnerId(), id);
        run(redirect, new InjectionRuleRequest(type, target, delayMs, httpStatus, remaining), r -> {
            injections.add(c, r);
            return "Rule added: " + type + " on " + target;
        });
        return redirect(id, "/injections");
    }

    @PostMapping("/injections/{ruleId}/delete")
    public String deleteInjection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @PathVariable UUID ruleId, RedirectAttributes redirect) {
        injections.delete(connections.owned(principal.partnerId(), id).getId(), ruleId);
        redirect.addFlashAttribute("message", "Rule deleted.");
        return redirect(id, "/injections");
    }

    /** htmx partial with the outcome of the bank's token request to the partner. */
    @SpecRef("sim.portal.endpoint")
    @PostMapping("/endpoint/test")
    public String testConnection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            Model model) {
        try {
            model.addAttribute("result", connectionTest.test(connections.owned(principal.partnerId(), id)));
        } catch (BusinessException e) {
            model.addAttribute("failure", e.getMessage());
        }
        return "portal/fragments/connection-test";
    }

    private interface Action<T> {
        String apply(T request);
    }

    /** Validates the request record, runs it and puts the outcome in a flash message. */
    private <T> void run(RedirectAttributes redirect, T request, Action<T> action) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            redirect.addFlashAttribute("error", violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().reduce((a, b) -> a + "; " + b).orElseThrow());
            return;
        }
        try {
            redirect.addFlashAttribute("message", action.apply(request));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
    }

    private static String describe(PaymentResult result) {
        return (result.notificationStatus() == null ? "No payment" : "Payment " + result.notificationStatus())
                + ". " + result.message();
    }
}
