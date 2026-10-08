package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.AccessToken;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.repository.AccessTokenRepository;
import com.artivisi.snapsimulator.util.Randoms;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TokenService {

    private static final int TOKEN_LENGTH = 64;

    private final AccessTokenRepository tokens;
    private final ChecklistService checklist;

    public TokenService(AccessTokenRepository tokens, ChecklistService checklist) {
        this.tokens = tokens;
        this.checklist = checklist;
    }

    /** Each request gets a new token; earlier ones stay valid until they expire (A14). */
    @Transactional
    @SpecRef("bri.oauth.token-b2b")
    public AccessToken issue(Partner partner, Instant now) {
        AccessToken token = new AccessToken();
        token.setPartner(partner);
        token.setToken(Randoms.alphanumeric(TOKEN_LENGTH));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusSeconds(partner.getTokenTtlSeconds()));
        tokens.save(token);
        checklist.stamp(partner.getId(), ChecklistItem.TOKEN_OBTAINED, now);
        return token;
    }

    /** The token's partner, if the token exists and has not expired. */
    public Optional<Partner> resolve(String token, Instant now) {
        return tokens.findByToken(token).filter(t -> t.getExpiresAt().isAfter(now)).map(AccessToken::getPartner);
    }
}
