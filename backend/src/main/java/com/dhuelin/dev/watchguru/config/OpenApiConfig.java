package com.dhuelin.dev.watchguru.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The published API contract.
 *
 * <p>Both mobile clients are generated from this document rather than
 * hand-written, so it is the actual interface between three codebases. A change
 * here that is not regenerated shows up as a 400 on a device; the spec-drift
 * check in CI exists to make it show up as a failed build instead.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    // No license object. OpenAPI 3.1 wants a License to carry an SPDX
    // identifier or a URL, and this project has neither -- "Proprietary" is not
    // an SPDX id. Declaring a name alone fails openapi-generator's validation
    // outright, which would block the very thing this document exists for.
    @Bean
    OpenAPI watchGuruOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Watch Guru API")
                        .version("v1")
                        .description("""
                                Tracking what you have watched and where you left off, for films \
                                and TV series.

                                Every endpoint under /api/v1 requires an OIDC bearer token from a \
                                trusted issuer (Sign in with Apple or Google). The account is \
                                created on the first valid token, so there is no registration \
                                call. Endpoints act on the user the token identifies -- there is \
                                no way to name a different one.

                                Catalog metadata comes from TMDB. This product uses the TMDB API \
                                but is not endorsed or certified by TMDB."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("OIDC ID token from Sign in with Apple or Google.")))
                // Applied globally: the handful of unauthenticated paths
                // (health, the spec itself) are not part of this document.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
