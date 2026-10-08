package com.artivisi.snapsimulator.controller;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.GeneratedKey;
import com.artivisi.snapsimulator.dto.IssuedCredentials;
import com.artivisi.snapsimulator.dto.PartnerView;
import com.artivisi.snapsimulator.dto.form.EndpointForm;
import com.artivisi.snapsimulator.dto.form.SettingsForm;
import com.artivisi.snapsimulator.dto.form.SignupForm;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.exception.BusinessException;
import com.artivisi.snapsimulator.security.PartnerPrincipal;
import com.artivisi.snapsimulator.service.PartnerDataService;
import com.artivisi.snapsimulator.service.PartnerService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/portal")
public class PortalController {

    private static final String REDIRECT_PORTAL = "redirect:/portal";

    private final PartnerService partners;
    private final PartnerDataService partnerData;

    public PortalController(PartnerService partners, PartnerDataService partnerData) {
        this.partners = partners;
        this.partnerData = partnerData;
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
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "portal/signup";
        }
        try {
            IssuedCredentials issued = partners.signup(form.toRequest());
            model.addAttribute("issued", issued);
            return "portal/issued";
        } catch (BusinessException e) {
            result.rejectValue(e.field(), "rejected", e.getMessage());
            return "portal/signup";
        }
    }

    @SpecRef("sim.portal.checklist")
    @GetMapping
    public String dashboard(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        Partner partner = partners.get(principal.partnerId());
        model.addAttribute("partner", PartnerView.of(partner));
        return "portal/dashboard";
    }

    @SpecRef("sim.portal.credentials")
    @PostMapping("/credentials/secret")
    public String regenerateSecret(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("issued", partners.regenerateSecret(principal.partnerId()));
        return "portal/issued";
    }

    @GetMapping("/key")
    public String key(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("publicKeyPem", partners.get(principal.partnerId()).getPublicKeyPem());
        return "portal/key";
    }

    @SpecRef("sim.portal.key-upload")
    @PostMapping("/key")
    public String uploadKey(@AuthenticationPrincipal PartnerPrincipal principal,
            @RequestParam(name = "publicKeyPem", required = false) String pasted,
            @RequestParam(name = "publicKeyFile", required = false) MultipartFile file,
            Model model, RedirectAttributes redirect) throws IOException {
        String pem = file != null && !file.isEmpty() ? new String(file.getBytes(), StandardCharsets.US_ASCII) : pasted;
        if (pem == null || pem.isBlank()) {
            return keyError(principal, model, "Paste the public key or choose a .pem file.");
        }
        try {
            partners.registerPublicKey(principal.partnerId(), pem);
        } catch (BusinessException e) {
            return keyError(principal, model, e.getMessage());
        }
        redirect.addFlashAttribute("message", "Public key registered.");
        return "redirect:/portal/key";
    }

    private String keyError(PartnerPrincipal principal, Model model, String message) {
        model.addAttribute("keyError", message);
        model.addAttribute("publicKeyPem", partners.get(principal.partnerId()).getPublicKeyPem());
        return "portal/key";
    }

    /** Stores the public key and sends the private key as a download; it is not kept. */
    @SpecRef("sim.portal.key-generate")
    @PostMapping("/key/generate")
    public ResponseEntity<String> generateKey(@AuthenticationPrincipal PartnerPrincipal principal) {
        GeneratedKey key = partners.generateKey(principal.partnerId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("private.pem").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(key.privateKeyPem());
    }

    @GetMapping("/settings")
    public String settingsForm(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("form", SettingsForm.of(partners.get(principal.partnerId())));
        return "portal/settings";
    }

    @SpecRef("sim.portal.settings")
    @PostMapping("/settings")
    public String settings(@AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @ModelAttribute("form") SettingsForm form, BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "portal/settings";
        }
        partners.updateSettings(principal.partnerId(), form.toRequest());
        redirect.addFlashAttribute("message", "Settings saved.");
        return REDIRECT_PORTAL;
    }

    @GetMapping("/endpoint")
    public String endpointForm(@AuthenticationPrincipal PartnerPrincipal principal, Model model) {
        model.addAttribute("form", EndpointForm.of(partners.get(principal.partnerId())));
        return "portal/endpoint";
    }

    @SpecRef("sim.portal.endpoint")
    @PostMapping("/endpoint")
    public String endpoint(@AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @ModelAttribute("form") EndpointForm form, BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "portal/endpoint";
        }
        partners.updateEndpoint(principal.partnerId(), form.toRequest());
        redirect.addFlashAttribute("message", "Partner endpoint saved.");
        return "redirect:/portal/endpoint";
    }

    @SpecRef("sim.portal.reset")
    @PostMapping("/reset")
    public String reset(@AuthenticationPrincipal PartnerPrincipal principal, RedirectAttributes redirect) {
        partnerData.reset(principal.partnerId());
        redirect.addFlashAttribute("message", "Simulation data reset.");
        return REDIRECT_PORTAL;
    }
}
