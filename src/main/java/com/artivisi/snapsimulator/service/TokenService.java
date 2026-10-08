package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.AccessToken;
import com.artivisi.snapsimulator.entity.BankConnection;
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
    @SpecRef("bca.oauth.token-b2b")
    public AccessToken issue(BankConnection connection, Instant now) {
        AccessToken token = new AccessToken();
        token.setConnection(connection);
        token.setToken(Randoms.alphanumeric(TOKEN_LENGTH));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusSeconds(connection.getTokenTtlSeconds()));
        tokens.save(token);
        checklist.stamp(connection.getId(), ChecklistItem.TOKEN_OBTAINED, now);
        return token;
    }

    /** The token's connection, if the token exists and has not expired. */
    public Optional<BankConnection> resolve(String token, Instant now) {
        return tokens.findByToken(token).filter(t -> t.getExpiresAt().isAfter(now)).map(AccessToken::getConnection);
    }
}
