package com.dhuelin.dev.watchguru.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Requires the token's {@code aud} to name this application.
 *
 * <p>Signature and issuer checks answer "did Google sign this", which is not the
 * same question as "was this token minted for us". Apple and Google issue tokens
 * to any registered client; without this check a token obtained by an unrelated
 * app whose users also sign in with Google could be replayed here and would
 * validate perfectly.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> allowed;

    public AudienceValidator(List<String> allowed) {
        this.allowed = List.copyOf(allowed);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.stream().anyMatch(allowed::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        // The message deliberately does not echo the received audience: it goes
        // to the client, and repeating an attacker's value back is how log and
        // response injection starts.
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "The token audience is not accepted by this API",
                "https://datatracker.ietf.org/doc/html/rfc6750#section-3.1"));
    }
}
