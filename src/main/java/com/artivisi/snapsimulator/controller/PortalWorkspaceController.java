package com.artivisi.snapsimulator.controller;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.BillerPaymentRequest;
import com.artivisi.snapsimulator.dto.ConnectionTestResult;
import com.artivisi.snapsimulator.dto.ExchangeView;
import com.artivisi.snapsimulator.dto.InjectionRuleRequest;
import com.artivisi.snapsimulator.dto.PaymentResult;
import com.artivisi.snapsimulator.dto.SeedRequest;
import com.artivisi.snapsimulator.dto.VaPayRequest;
import com.artivisi.snapsimulator.enums.Channel;
import com.artivisi.snapsimulator.enums.InjectionType;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.security.PartnerPrincipal;
import com.artivisi.snapsimulator.service.ConnectionTestService;
import com.artivisi.snapsimulator.service.ExchangeLogService;
import com.artivisi.snapsimulator.service.InjectionService;
import com.artivisi.snapsimulator.service.PartnerService;
import com.artivisi.snapsimulator.service.PaymentService;
import com.artivisi.snapsimulator.service.ReconciliationSeeder;
import com.artivisi.snapsimulator.service.VirtualAccountService;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import com.artivisi.snapsimulator.snap.SnapService;
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

/** Pages for working with the simulator once onboarded: VAs, payments, exchange log, injection. */
@Controller
@RequestMapping("/portal")
public class PortalWorkspaceController {

    private static final int EXCHANGE_PAGE = 50;

    private final PartnerService partners;
    private final VirtualAccountService accounts;
    private final PaymentService payments;
    private final ReconciliationSeeder seeder;
    private final ExchangeLogService exchangeLog;
    private final InjectionService injections;
    private final ConnectionTestService connectionTest;
    private final Validator validator;

    public PortalWorkspaceController(PartnerService partners, VirtualAccountService accounts, PaymentService payments,
            ReconciliationSeeder seeder, ExchangeLogService exchangeLog, InjectionService injections,
            ConnectionTestService connectionTest, Validator validator) {
        this.partners = partners;
        this.accounts = accounts;
        this.payments = payments;
        this.seeder = seeder;
        this.exchangeLog = exchangeLog;
        this.injections = injections;
        this.connectionTest = connectionTest;
        this.validator = validator;
    }

    @GetMapping("/vas")
    public String virtualAccounts(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("vas", accounts.list(principal.partnerId()));
        model.addAttribute("channels", Channel.values());
        return "portal/vas";
    }

    @SpecRef("sim.va.pay")
    @PostMapping("/vas/pay")
    public String payVa(@AuthenticationPrincipal PartnerPrincipal principal, @RequestParam String virtualAccountNo,
            @RequestParam String channelId, RedirectAttributes redirect) {
        run(redirect, new VaPayRequest(virtualAccountNo, channelId),
                r -> describe(payments.payBankHosted(principal.partnerId(), r)));
        return "redirect:/portal/vas";
    }

    @GetMapping("/payments")
    public String payments(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("payments", payments.list(principal.partnerId()));
        model.addAttribute("channels", Channel.values());
        model.addAttribute("partnerServiceId", partners.get(principal.partnerId()).getPartnerServiceId());
        model.addAttribute("today", LocalDate.now(SnapTimestamp.JAKARTA).toString());
        return "portal/payments";
    }

    @SpecRef("sim.biller-payment.trigger")
    @PostMapping("/payments/biller")
    public String billerPayment(@AuthenticationPrincipal PartnerPrincipal principal, @RequestParam String virtualAccountNo,
            @RequestParam String amount, @RequestParam String channelId, RedirectAttributes redirect) {
        run(redirect, new BillerPaymentRequest(virtualAccountNo, amount, channelId),
                r -> describe(payments.payBillerHosted(principal.partnerId(), r)));
        return "redirect:/portal/payments";
    }

    @SpecRef("sim.biller-payment.resend")
    @PostMapping("/payments/{id}/resend")
    public String resend(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            RedirectAttributes redirect) {
        run(redirect, id, paymentId -> payments.resend(principal.partnerId(), paymentId).message());
        return "redirect:/portal/payments";
    }

    @SpecRef("sim.reconciliation-seeder")
    @PostMapping("/payments/seed")
    public String seed(@AuthenticationPrincipal PartnerPrincipal principal, @RequestParam String virtualAccountNos,
            @RequestParam String channelId, RedirectAttributes redirect) {
        List<String> numbers = Arrays.stream(virtualAccountNos.split("\\R")).filter(s -> !s.isBlank())
                .map(s -> s.replaceAll("\\s+$", "")).toList();
        run(redirect, new SeedRequest(numbers, channelId), r -> {
            StringBuilder sb = new StringBuilder("Seeded: ");
            seeder.seed(principal.partnerId(), r).forEach(s -> sb.append(s.scenario()).append(' ')
                    .append(s.result().notificationStatus()).append("; "));
            return sb.toString();
        });
        return "redirect:/portal/payments";
    }

    @SpecRef("sim.exchange-log")
    @GetMapping("/exchanges")
    public String exchanges(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("exchanges", exchangeLog.recent(principal.partnerId(), EXCHANGE_PAGE).stream()
                .map(ExchangeView::of).toList());
        return "portal/exchanges";
    }

    @GetMapping("/injections")
    public String injectionRules(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("rules", injections.list(principal.partnerId()));
        model.addAttribute("types", InjectionType.values());
        Map<InjectionType, List<String>> targets = new LinkedHashMap<>();
        for (InjectionType type : InjectionType.values()) {
            targets.put(type, InjectionService.validTargets(type));
        }
        model.addAttribute("targets", targets);
        model.addAttribute("services", SnapService.values());
        return "portal/injections";
    }

    @SpecRef("sim.error-injection")
    @PostMapping("/injections")
    public String addInjection(@AuthenticationPrincipal PartnerPrincipal principal, @RequestParam InjectionType type,
            @RequestParam String target, @RequestParam(required = false) Long delayMs,
            @RequestParam(required = false) Integer httpStatus, @RequestParam Integer remaining,
            RedirectAttributes redirect) {
        run(redirect, new InjectionRuleRequest(type, target, delayMs, httpStatus, remaining), r -> {
            injections.add(principal.partnerId(), r);
            return "Rule added: " + type + " on " + target;
        });
        return "redirect:/portal/injections";
    }

    @PostMapping("/injections/{id}/delete")
    public String deleteInjection(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            RedirectAttributes redirect) {
        injections.delete(principal.partnerId(), id);
        redirect.addFlashAttribute("message", "Rule deleted.");
        return "redirect:/portal/injections";
    }

    /** htmx partial with the outcome of the bank's token request to the partner. */
    @SpecRef("sim.portal.endpoint")
    @PostMapping("/endpoint/test")
    public String testConnection(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        try {
            ConnectionTestResult result = connectionTest.test(principal.partnerId());
            model.addAttribute("result", result);
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
