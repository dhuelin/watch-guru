package com.dhuelin.dev.watchguru.security;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provisioning and account-linking rules.
 *
 * <p>These are the decisions that determine whether one person's token can
 * reach another person's watch history, so they are tested directly rather than
 * only through the API.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CurrentUserServiceTest {

    private static final String APPLE = "https://appleid.apple.com";
    private static final String GOOGLE = "https://accounts.google.com";
    private static final String UNTRUSTED = "https://sso.example.test";

    private AppUserRepository users;
    private CurrentUserService service;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        AuthProperties properties = new AuthProperties(
                List.of(
                        new AuthProperties.Issuer("apple", APPLE, true),
                        new AuthProperties.Issuer("google", GOOGLE, true),
                        new AuthProperties.Issuer("legacy", UNTRUSTED, false)),
                List.of("watch-guru-app"),
                true);
        service = new CurrentUserService(users, properties);

        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
        when(users.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static Jwt jwt(String issuer, String subject, String email, Object emailVerified, String name) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (email != null) {
            builder.claim("email", email);
        }
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        if (name != null) {
            builder.claim("name", name);
        }
        return builder.build();
    }

    @Test
    @DisplayName("first sign-in creates the account")
    void firstSignInProvisions() {
        when(users.findByAuthSubject(any())).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        AppUser user = service.resolve(jwt(GOOGLE, "g-1", "Viewer@Example.com", true, "Viewer"));

        assertThat(user.getAuthSubject()).isEqualTo(GOOGLE + "|g-1");
        assertThat(user.getAuthIssuer()).isEqualTo(GOOGLE);
        assertThat(user.getDisplayName()).isEqualTo("Viewer");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("email is normalised to lower case so the unique index is not case-sensitive")
    void emailIsLowercased() {
        when(users.findByAuthSubject(any())).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        AppUser user = service.resolve(jwt(GOOGLE, "g-1", "Viewer@Example.COM", true, null));

        assertThat(user.getEmail()).isEqualTo("viewer@example.com");
    }

    @Test
    @DisplayName("returning user is matched on subject, not email")
    void returningUserResolvesBySubject() {
        AppUser existing = new AppUser("viewer@example.com", "Viewer");
        existing.setAuthSubject(GOOGLE + "|g-1");
        when(users.findByAuthSubject(GOOGLE + "|g-1")).thenReturn(Optional.of(existing));

        AppUser user = service.resolve(jwt(GOOGLE, "g-1", "different@example.com", true, null));

        assertThat(user).isSameAs(existing);
        assertThat(user.getEmail()).isEqualTo("viewer@example.com");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the same subject string from two issuers is two different accounts")
    void subjectIsNamespacedByIssuer() {
        Jwt fromApple = jwt(APPLE, "collide", "a@example.com", true, null);
        Jwt fromGoogle = jwt(GOOGLE, "collide", "b@example.com", true, null);

        assertThat(CurrentUserService.namespacedSubject(fromApple))
                .isNotEqualTo(CurrentUserService.namespacedSubject(fromGoogle));
    }

    @Test
    @DisplayName("a trusted issuer's verified email adopts the existing account")
    void trustedIssuerLinksOnVerifiedEmail() {
        AppUser existing = new AppUser("viewer@example.com", "Viewer");
        existing.setAuthSubject(GOOGLE + "|g-1");
        existing.setAuthIssuer(GOOGLE);
        existing.setEmailVerified(true);

        when(users.findByAuthSubject(APPLE + "|a-1")).thenReturn(Optional.empty());
        when(users.findByEmail("viewer@example.com")).thenReturn(Optional.of(existing));

        AppUser user = service.resolve(jwt(APPLE, "a-1", "viewer@example.com", true, null));

        assertThat(user.getId()).isEqualTo(existing.getId());
        assertThat(user.getAuthSubject()).isEqualTo(APPLE + "|a-1");
        assertThat(user.getAuthIssuer()).isEqualTo(APPLE);
    }

    @Test
    @DisplayName("an unverified email cannot reach an existing account")
    void unverifiedEmailIsRejected() {
        AppUser existing = new AppUser("viewer@example.com", "Viewer");
        existing.setAuthSubject(GOOGLE + "|g-1");
        existing.setEmailVerified(true);

        when(users.findByAuthSubject(APPLE + "|a-1")).thenReturn(Optional.empty());
        when(users.findByEmail("viewer@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resolve(jwt(APPLE, "a-1", "viewer@example.com", false, null)))
                .isInstanceOf(AccountConflictException.class);

        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("an untrusted issuer cannot link even with email_verified set")
    void untrustedIssuerCannotLink() {
        AppUser existing = new AppUser("viewer@example.com", "Viewer");
        existing.setAuthSubject(GOOGLE + "|g-1");
        existing.setEmailVerified(true);

        when(users.findByAuthSubject(UNTRUSTED + "|x-1")).thenReturn(Optional.empty());
        when(users.findByEmail("viewer@example.com")).thenReturn(Optional.of(existing));

        // The issuer asserts the address is verified. It is configured as not
        // trusted to make that assertion, so the claim counts for nothing --
        // this is the case that would otherwise be an account takeover.
        assertThatThrownBy(() -> service.resolve(jwt(UNTRUSTED, "x-1", "viewer@example.com", true, null)))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    @DisplayName("Apple's string-valued email_verified counts as verified")
    void stringEmailVerifiedIsAccepted() {
        AppUser existing = new AppUser("viewer@example.com", "Viewer");
        existing.setAuthSubject(GOOGLE + "|g-1");
        existing.setEmailVerified(true);

        when(users.findByAuthSubject(APPLE + "|a-1")).thenReturn(Optional.empty());
        when(users.findByEmail("viewer@example.com")).thenReturn(Optional.of(existing));

        // Apple has historically sent "true" rather than true. Reading only the
        // boolean form would break linking for every Apple user.
        AppUser user = service.resolve(jwt(APPLE, "a-1", "viewer@example.com", "true", null));

        assertThat(user.getAuthSubject()).isEqualTo(APPLE + "|a-1");
    }

    @Test
    @DisplayName("a token with no email gets a unique, undeliverable placeholder")
    void missingEmailGetsPlaceholder() {
        when(users.findByAuthSubject(any())).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        AppUser user = service.resolve(jwt(APPLE, "a-nomail", null, null, null));

        assertThat(user.getEmail()).endsWith("@users.noreply.watch-guru.invalid");
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(CurrentUserService.placeholderEmail(APPLE + "|a-nomail"))
                .isNotEqualTo(CurrentUserService.placeholderEmail(APPLE + "|a-other"));
    }

    @Test
    @DisplayName("display name falls back to the email local part, never the whole address")
    void displayNameFallsBackToLocalPart() {
        when(users.findByAuthSubject(any())).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        AppUser user = service.resolve(jwt(GOOGLE, "g-2", "viewer@example.com", true, null));

        assertThat(user.getDisplayName()).isEqualTo("viewer");
    }

    @Test
    @DisplayName("an over-long display name is truncated to what the column holds")
    void longDisplayNameIsTruncated() {
        when(users.findByAuthSubject(any())).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        AppUser user = service.resolve(jwt(GOOGLE, "g-3", "a@b.com", true, "x".repeat(500)));

        assertThat(user.getDisplayName()).hasSize(128);
    }
}
