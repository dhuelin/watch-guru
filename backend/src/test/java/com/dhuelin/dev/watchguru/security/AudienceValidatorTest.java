package com.dhuelin.dev.watchguru.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator =
            new AudienceValidator(List.of("watch-guru-ios", "watch-guru-android"));

    private static Jwt withAudience(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    @DisplayName("a token for one of our apps passes")
    void acceptsKnownAudience() {
        assertThat(validator.validate(withAudience(List.of("watch-guru-ios"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("a token for somebody else's app is rejected")
    void rejectsForeignAudience() {
        // The case this validator exists for: correctly signed by Google, for a
        // completely different application, replayed here.
        OAuth2TokenValidatorResult result = validator.validate(withAudience(List.of("some-other-app")));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).allSatisfy(error ->
                assertThat(error.getDescription()).doesNotContain("some-other-app"));
    }

    @Test
    @DisplayName("a token with no audience at all is rejected")
    void rejectsMissingAudience() {
        assertThat(validator.validate(withAudience(null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("one matching audience among several is enough")
    void acceptsWhenOneOfSeveralMatches() {
        assertThat(validator.validate(withAudience(List.of("other", "watch-guru-android"))).hasErrors())
                .isFalse();
    }
}
