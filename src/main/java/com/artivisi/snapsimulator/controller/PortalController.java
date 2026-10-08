package com.artivisi.snapsimulator.controller;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.GeneratedKey;
import com.artivisi.snapsimulator.dto.form.ConnectionForm;
import com.artivisi.snapsimulator.dto.form.EndpointForm;
import com.artivisi.snapsimulator.dto.form.SettingsForm;
import com.artivisi.snapsimulator.dto.form.SignupForm;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.enums.Bank;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.security.PartnerPrincipal;
import com.artivisi.snapsimulator.service.ConnectionService;
import com.artivisi.snapsimulator.service.PartnerDataService;
import com.artivisi.snapsimulator.service.PartnerService;
import com.artivisi.snapsimulator.snap.SnapService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Account, bank connections and their onboarding pages. */
@Controller
@RequestMapping("/portal")
public class PortalController {

    private final PartnerService partners;
    private final ConnectionService connections;
    private final PartnerDataService partnerData;

    public PortalController(PartnerService partners, ConnectionService connections, PartnerDataService partnerData) {
        this.partners = partners;
        this.connections = connections;
        this.partnerData = partnerData;
    }

    /** Puts the connection (as "conn") into the model for the connection navigation. */
    static BankConnection connection(ConnectionService connections, PartnerPrincipal principal, UUID id, Model model) {
        BankConnection c = connections.owned(principal.partnerId(), id);
        model.addAttribute("conn", connections.view(c));
        return c;
    }

    private static String redirect(UUID id, String page) {
        return "redirect:/portal/c/" + id + page;
    }

    @GetMapping("/login")
    public String login() {
        return "portal/login";
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("form", new SignupForm());
        return "portal/signup";
    }

    @SpecRef("sim.portal.signup")
    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult result) {
        if (result.hasErrors()) {
            return "portal/signup";
        }
        try {
            partners.signup(form.toRequest());
        } catch (BusinessException e) {
            result.rejectValue(e.field(), "rejected", e.getMessage());
            return "portal/signup";
        }
        return "redirect:/portal/login?registered";
    }

    @SpecRef("sim.portal.connection")
    @GetMapping
    public String account(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("account", connections.account(principal.partnerId()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ConnectionForm());
        }
        model.addAttribute("banks", Bank.values());
        return "portal/account";
    }

    @SpecRef("sim.portal.connection")
    @PostMapping("/connections")
    public String addConnection(@AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @ModelAttribute("form") ConnectionForm form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("account", connections.account(principal.partnerId()));
            model.addAttribute("banks", Bank.values());
            return "portal/account";
        }
        model.addAttribute("issued", connections.create(principal.partnerId(), form.toRequest()));
        return "portal/issued";
    }

    @SpecRef("sim.portal.checklist")
    @GetMapping("/c/{id}")
    public String dashboard(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id, Model model) {
        BankConnection c = connection(connections, principal, id, model);
        model.addAttribute("services", SnapService.of(c.getBank()));
        return "portal/dashboard";
    }

    @SpecRef("sim.portal.credentials")
    @PostMapping("/c/{id}/credentials/secret")
    public String regenerateSecret(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            Model model) {
        model.addAttribute("issued", connections.regenerateSecret(principal.partnerId(), id));
        return "portal/issued";
    }

    @GetMapping("/c/{id}/key")
    public String key(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id, Model model) {
        model.addAttribute("publicKeyPem", connection(connections, principal, id, model).getPublicKeyPem());
        return "portal/key";
    }

    @SpecRef("sim.portal.key-upload")
    @PostMapping("/c/{id}/key")
    public String uploadKey(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @RequestParam(name = "publicKeyPem", required = false) String pasted,
            @RequestParam(name = "publicKeyFile", required = false) MultipartFile file,
            Model model, RedirectAttributes redirect) throws IOException {
        String pem = file != null && !file.isEmpty() ? new String(file.getBytes(), StandardCharsets.US_ASCII) : pasted;
        try {
            if (pem == null || pem.isBlank()) {
                throw new BusinessException("publicKeyPem", "Paste the public key or choose a .pem file.");
            }
            connections.registerPublicKey(principal.partnerId(), id, pem);
        } catch (BusinessException e) {
            model.addAttribute("keyError", e.getMessage());
            model.addAttribute("publicKeyPem", connection(connections, principal, id, model).getPublicKeyPem());
            return "portal/key";
        }
        redirect.addFlashAttribute("message", "Public key registered.");
        return redirect(id, "/key");
    }

    /** Stores the public key and sends the private key as a download; it is not kept. */
    @SpecRef("sim.portal.key-generate")
    @PostMapping("/c/{id}/key/generate")
    public ResponseEntity<String> generateKey(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id) {
        GeneratedKey key = connections.generateKey(principal.partnerId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("private.pem").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(key.privateKeyPem());
    }

    @GetMapping("/c/{id}/settings")
    public String settingsForm(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id, Model model) {
        model.addAttribute("form", SettingsForm.of(connection(connections, principal, id, model)));
        return "portal/settings";
    }

    @SpecRef("sim.portal.settings")
    @PostMapping("/c/{id}/settings")
    public String settings(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @ModelAttribute("form") SettingsForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            connection(connections, principal, id, model);
            return "portal/settings";
        }
        connections.updateSettings(principal.partnerId(), id, form.toRequest());
        redirect.addFlashAttribute("message", "Settings saved.");
        return redirect(id, "");
    }

    @GetMapping("/c/{id}/endpoint")
    public String endpointForm(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id, Model model) {
        BankConnection c = connection(connections, principal, id, model);
        model.addAttribute("form", EndpointForm.of(c));
        return "portal/endpoint";
    }

    @SpecRef("sim.portal.endpoint")
    @PostMapping("/c/{id}/endpoint")
    public String endpoint(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            @Valid @ModelAttribute("form") EndpointForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            connection(connections, principal, id, model);
            return "portal/endpoint";
        }
        connections.updateEndpoint(principal.partnerId(), id, form.toRequest());
        redirect.addFlashAttribute("message", "Partner endpoint saved.");
        return redirect(id, "/endpoint");
    }

    @SpecRef("sim.portal.reset")
    @PostMapping("/c/{id}/reset")
    public String reset(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID id,
            RedirectAttributes redirect) {
        partnerData.reset(connections.owned(principal.partnerId(), id).getId());
        redirect.addFlashAttribute("message", "Simulation data reset.");
        return redirect(id, "");
    }
}
