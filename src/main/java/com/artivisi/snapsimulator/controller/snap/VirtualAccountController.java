package com.artivisi.snapsimulator.controller.snap;

import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.service.VirtualAccountService;
import com.artivisi.snapsimulator.snap.SnapService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;

@RestController
@RequestMapping("/snap/v1.0/transfer-va")
public class VirtualAccountController {

    private final VirtualAccountService accounts;
    private final JsonMapper json;
    private final Clock clock;

    public VirtualAccountController(VirtualAccountService accounts, JsonMapper json, Clock clock) {
        this.accounts = accounts;
        this.json = json;
        this.clock = clock;
    }

    @PostMapping("/create-va")
    public ObjectNode create(@RequestAttribute(SnapInboundFilter.PARTNER) Partner partner,
            @RequestBody(required = false) String body) {
        return accounts.create(partner, parse(body, SnapService.CREATE_VA), clock.instant());
    }

    @PutMapping("/update-va")
    public ObjectNode update(@RequestAttribute(SnapInboundFilter.PARTNER) Partner partner,
            @RequestBody(required = false) String body) {
        return accounts.update(partner, parse(body, SnapService.UPDATE_VA), clock.instant());
    }

    @PostMapping("/inquiry-va")
    public ObjectNode inquiry(@RequestAttribute(SnapInboundFilter.PARTNER) Partner partner,
            @RequestBody(required = false) String body) {
        return accounts.inquiry(partner, parse(body, SnapService.INQUIRY_VA), clock.instant());
    }

    @DeleteMapping("/delete-va")
    public ObjectNode delete(@RequestAttribute(SnapInboundFilter.PARTNER) Partner partner,
            @RequestBody(required = false) String body) {
        return accounts.delete(partner, parse(body, SnapService.DELETE_VA));
    }

    @PostMapping("/status")
    public ObjectNode status(@RequestAttribute(SnapInboundFilter.PARTNER) Partner partner,
            @RequestBody(required = false) String body) {
        return accounts.status(partner, parse(body, SnapService.INQUIRY_STATUS));
    }

    private JsonNode parse(String body, SnapService svc) {
        try {
            return json.readTree(body == null ? "" : body);
        } catch (JacksonException e) {
            throw new SnapException(svc.badRequest());
        }
    }
}
