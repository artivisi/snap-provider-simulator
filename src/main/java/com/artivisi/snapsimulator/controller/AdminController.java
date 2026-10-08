package com.artivisi.snapsimulator.controller;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.dto.AccountView;
import com.artivisi.snapsimulator.service.ConnectionService;
import com.artivisi.snapsimulator.service.PartnerDataService;
import com.artivisi.snapsimulator.service.PartnerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin")
@SpecRef("sim.admin.partners")
public class AdminController {

    private static final String REDIRECT_ADMIN = "redirect:/admin";

    private final PartnerService partners;
    private final ConnectionService connections;
    private final PartnerDataService partnerData;

    public AdminController(PartnerService partners, ConnectionService connections, PartnerDataService partnerData) {
        this.partners = partners;
        this.connections = connections;
        this.partnerData = partnerData;
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    public record Row(UUID id, boolean enabled, AccountView account) {
    }

    @GetMapping
    public String partners(Model model) {
        model.addAttribute("rows", partners.listAll().stream()
                .map(p -> new Row(p.getId(), p.isEnabled(), connections.account(p.getId()))).toList());
        return "admin/partners";
    }

    @PostMapping("/partners/{id}/enable")
    public String enable(@PathVariable UUID id, RedirectAttributes redirect) {
        partners.setEnabled(id, true);
        redirect.addFlashAttribute("message", "Partner enabled.");
        return REDIRECT_ADMIN;
    }

    @PostMapping("/partners/{id}/disable")
    public String disable(@PathVariable UUID id, RedirectAttributes redirect) {
        partners.setEnabled(id, false);
        redirect.addFlashAttribute("message", "Partner disabled.");
        return REDIRECT_ADMIN;
    }

    @SpecRef("sim.portal.reset")
    @PostMapping("/connections/{id}/reset")
    public String reset(@PathVariable UUID id, RedirectAttributes redirect) {
        partnerData.reset(connections.get(id).getId());
        redirect.addFlashAttribute("message", "Connection data reset.");
        return REDIRECT_ADMIN;
    }

    @PostMapping("/partners/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        partners.delete(id);
        redirect.addFlashAttribute("message", "Partner deleted.");
        return REDIRECT_ADMIN;
    }
}
