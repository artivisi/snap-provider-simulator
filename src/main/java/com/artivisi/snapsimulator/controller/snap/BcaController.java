package com.artivisi.snapsimulator.controller.snap;

import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.service.BcaStatusService;
import com.artivisi.snapsimulator.snap.SnapService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** BCA services a partner calls: inquiry status (the token is in AccessTokenController). */
@RestController
public class BcaController {

    private final BcaStatusService status;
    private final JsonMapper json;

    public BcaController(BcaStatusService status, JsonMapper json) {
        this.status = status;
        this.json = json;
    }

    @PostMapping("/openapi/v2.0/transfer-va/status")
    public ObjectNode status(@RequestAttribute(SnapInboundFilter.CONNECTION) BankConnection connection,
            @RequestBody(required = false) String body) {
        try {
            return status.status(connection, json.readTree(body == null ? "" : body));
        } catch (JacksonException e) {
            throw new SnapException(SnapService.BCA_INQUIRY_STATUS.badRequest());
        }
    }
}
