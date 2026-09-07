package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.catalog.service.CatalogService;
import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.config.OpenApiConfig;
import com.dhuelin.dev.watchguru.config.TmdbProperties;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.SecurityConfig;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.service.StatsService;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Generates the OpenAPI document and holds the committed copy to it.
 *
 * <p>Both mobile clients are generated from {@code openapi.json}. If it drifts
 * from the code, the apps are built against an API that no longer exists and
 * the first anyone hears of it is a 400 on a device. This test turns that into
 * a failed build.
 *
 * <p>Runs as a web-slice test with every collaborator mocked, deliberately: the
 * document depends only on controller signatures and DTO shapes, so making it
 * depend on a database container as well would be a fragile way to learn
 * nothing extra.
 *
 * <p>To update after an intentional API change:
 * {@code ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true}
 */
@WebMvcTest(controllers = {
        MeController.class,
        TitleController.class,
        WatchlistController.class,
        WatchHistoryController.class,
        StreamingController.class})
@Import({SecurityConfig.class, OpenApiConfig.class, OpenApiSpecTest.SpecTestConfig.class})
// @WebMvcTest applies only a fixed list of auto-configurations, and springdoc
// is not on it, so without this the /v3/api-docs handler is never registered
// and the endpoint 404s with an empty body.
@ImportAutoConfiguration({
        org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class})
class OpenApiSpecTest {

    private static final Path SPEC = Path.of("src/main/resources/openapi/openapi.json");

    @TestConfiguration(proxyBeanMethods = false)
    static class SpecTestConfig {

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(
                    List.of(new AuthProperties.Issuer("google", "https://accounts.google.com", true)),
                    List.of("watch-guru-test"),
                    true);
        }

        /** A record, so it is supplied rather than mocked. */
        @Bean
        TmdbProperties tmdbProperties() {
            return new TmdbProperties(
                    "https://api.themoviedb.org/3",
                    "https://image.tmdb.org/t/p",
                    "",
                    "en-US",
                    "US",
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10));
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean private CatalogService catalogService;
    @MockitoBean private AvailabilityService availabilityService;
    @MockitoBean private TitleRepository titleRepository;
    @MockitoBean private ApiMapper apiMapper;
    @MockitoBean private WatchlistService watchlistService;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private StatsService statsService;
    @MockitoBean private WatchEventRepository watchEventRepository;
    @MockitoBean private StreamingServiceRepository streamingServiceRepository;
    @MockitoBean private LinkedStreamingAccountRepository linkedStreamingAccountRepository;
    @MockitoBean private AppUserRepository appUserRepository;

    /**
     * Fetches the document, insisting the endpoint actually served one.
     *
     * <p>The status check is not ceremony. A misconfigured slice returns 404
     * with an empty body, which parses to a JSON null and would happily
     * overwrite the committed spec with the four characters "null".
     */
    private String fetchSpec() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        assertThat(response.getStatus())
                .as("/v3/api-docs did not serve a document; springdoc is not wired into this slice")
                .isEqualTo(200);
        String body = response.getContentAsString();
        assertThat(body).as("/v3/api-docs returned an empty body").isNotBlank();
        return body;
    }

    @Test
    @DisplayName("the committed OpenAPI spec matches the code")
    void specMatchesTheCode() throws Exception {
        String body = fetchSpec();

        ObjectMapper json = new ObjectMapper();
        ObjectWriter pretty = json.writerWithDefaultPrettyPrinter();
        JsonNode generated = json.readTree(body);
        String rendered = pretty.writeValueAsString(generated) + "\n";

        if (Boolean.getBoolean("openapi.write") || !Files.exists(SPEC)) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, rendered, StandardCharsets.UTF_8);
            // Not a silent pass: writing the file is an explicit action the
            // developer asked for, and the run should say so.
            System.out.println("Wrote " + SPEC.toAbsolutePath());
        }

        String committed = Files.readString(SPEC, StandardCharsets.UTF_8);
        assertThat(rendered)
                .as("openapi.json is stale. Regenerate with: "
                        + "./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true")
                .isEqualTo(committed);
    }

    @Test
    @DisplayName("the document describes the versioned API and no legacy user-id routes")
    void documentsTheVersionedApi() throws Exception {
        JsonNode paths = new ObjectMapper().readTree(fetchSpec()).get("paths");

        assertThat(paths).isNotNull();
        assertThat(paths.fieldNames()).toIterable()
                .isNotEmpty()
                .allSatisfy(path -> assertThat(path).startsWith("/api/v1/"));

        // The vulnerability this API just removed, asserted as absent so it
        // cannot be reintroduced without a failing test.
        assertThat(paths.fieldNames()).toIterable()
                .noneSatisfy(path -> assertThat(path).contains("{userId}"));
    }
}
