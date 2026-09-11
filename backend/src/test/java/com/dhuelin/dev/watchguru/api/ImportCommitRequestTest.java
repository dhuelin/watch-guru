package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.config.ImportProperties;
import com.dhuelin.dev.watchguru.imports.domain.MatchStatus;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.imports.service.ImportService;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.SecurityConfig;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.support.TestAuth;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the commit endpoint accepts, and what it turns away.
 *
 * <p>Both cases here came from review. A date can arrive in two shapes because
 * this project's own two generated clients disagree about how to encode one;
 * and a row missing its title id used to reach the writer, where it became a
 * 500 rather than the 400 it always was.
 */
@WebMvcTest(ImportController.class)
@Import({SecurityConfig.class, TrustedIssuers.class, AccessTokenIssuer.class,
        ImportCommitRequestTest.TestConfig.class})
class ImportCommitRequestTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        AuthProperties authProperties() {
            return TestAuth.properties();
        }

        @Bean
        ImportProperties importProperties() {
            return new ImportProperties(50);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ImportService imports;
    @MockitoBean
    private CurrentUserService currentUser;

    private void signedIn() {
        AppUser user = new AppUser("importer@example.com", "Importer");
        user.setId(1L);
        when(currentUser.require()).thenReturn(user);
        when(imports.commit(any(), any())).thenReturn(new ImportService.Result(1, 0, 0, List.of()));
    }

    @SuppressWarnings("unchecked")
    private List<MatchedRow> committedRows() {
        ArgumentCaptor<List<MatchedRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(imports).commit(any(), rows.capture());
        return rows.getValue();
    }

    @Test
    @DisplayName("a plain date is a date")
    void plainDate() throws Exception {
        signedIn();

        mvc.perform(post("/api/v1/me/imports").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"sourceRef":"wg:1","titleId":7,"watchedAt":"2024-01-15"}]}
                                """))
                .andExpect(status().isOk());

        assertThat(committedRows()).singleElement()
                .satisfies(row -> assertThat(row.row().watchedAt()).isEqualTo(LocalDate.of(2024, 1, 15)));
    }

    @Test
    @DisplayName("a date-time is the same date, because the iOS client sends one")
    void dateTimeIsAccepted() throws Exception {
        // The Swift generator maps every date to Date and encodes it with an
        // ISO-8601 date-time formatter. Rejecting that would 400 every dated
        // row imported from an iPhone.
        signedIn();

        mvc.perform(post("/api/v1/me/imports").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"sourceRef":"wg:1","titleId":7,"watchedAt":"2024-01-15T00:00:00Z"}]}
                                """))
                .andExpect(status().isOk());

        assertThat(committedRows()).singleElement()
                .satisfies(row -> assertThat(row.row().watchedAt()).isEqualTo(LocalDate.of(2024, 1, 15)));
    }

    @Test
    @DisplayName("a row with no title id is a 400, not a 500 further in")
    void missingTitleIdIsRejected() throws Exception {
        signedIn();

        mvc.perform(post("/api/v1/me/imports").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"sourceRef":"wg:1"}]}
                                """))
                .andExpect(status().isBadRequest());

        verify(imports, never()).commit(any(), any());
    }

    @Test
    @DisplayName("a rating outside the scale is a 400")
    void ratingOutOfRange() throws Exception {
        signedIn();

        mvc.perform(post("/api/v1/me/imports").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"sourceRef":"wg:1","titleId":7,"rating":11}]}
                                """))
                .andExpect(status().isBadRequest());

        verify(imports, never()).commit(any(), any());
    }

    @Test
    @DisplayName("the rating from the preview reaches the row that gets written")
    void ratingIsCarried() throws Exception {
        signedIn();

        mvc.perform(post("/api/v1/me/imports").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"sourceRef":"wg:1","titleId":7,"rating":8.5}]}
                                """))
                .andExpect(status().isOk());

        assertThat(committedRows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(MatchStatus.MATCHED);
            assertThat(row.row().rating()).isEqualByComparingTo("8.5");
        });
    }

    @Test
    @DisplayName("no token means 401")
    void anonymous() throws Exception {
        mvc.perform(post("/api/v1/me/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
