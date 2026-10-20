package com.dhuelin.dev.watchguru.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Reading the one variable every container host hands a database over in.
 *
 * <p>The cost of getting this wrong is an application that starts against no
 * database and fails later with an error that points nowhere near the cause,
 * so the cases here are the ones a real host actually produces: a password
 * that had to be escaped, a query string carrying sslmode, and a variable that
 * is not a database URL at all.
 */
class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor =
            new DatabaseUrlEnvironmentPostProcessor();

    @Test
    @DisplayName("a libpq URL becomes a JDBC URL and a separate user and password")
    void translatesTheUsualForm() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.translate(
                "postgres://guru:s3cret@watch-guru-db.flycast:5432/watch_guru"))
                .containsOnly(
                        entry("spring.datasource.url",
                                "jdbc:postgresql://watch-guru-db.flycast:5432/watch_guru"),
                        entry("spring.datasource.username", "guru"),
                        entry("spring.datasource.password", "s3cret"));
    }

    @Test
    @DisplayName("the query string survives, because that is where sslmode lives")
    void keepsTheQueryString() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.translate(
                "postgres://u:p@db.internal:5432/app?sslmode=disable"))
                .containsEntry("spring.datasource.url",
                        "jdbc:postgresql://db.internal:5432/app?sslmode=disable");
    }

    @Test
    @DisplayName("an escaped password is decoded, not passed through escaped")
    void decodesThePassword() {
        // A generated password routinely contains characters that had to be
        // escaped to fit in a URL. Handing the escaped form to the driver
        // fails authentication with a message that blames the credentials.
        assertThat(DatabaseUrlEnvironmentPostProcessor.translate(
                "postgres://u:p%40ss%2Fword@db:5432/app"))
                .containsEntry("spring.datasource.password", "p@ss/word");
    }

    @Test
    @DisplayName("the postgresql:// spelling is the same URL")
    void acceptsTheLongerScheme() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.translate(
                "postgresql://u:p@db:5432/app"))
                .containsEntry("spring.datasource.url", "jdbc:postgresql://db:5432/app");
    }

    @Test
    @DisplayName("something that is not a database URL is left alone")
    void ignoresAnythingElse() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.translate("mysql://u:p@db/app")).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.translate("not a url at all")).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.translate("postgres:///no-host")).isEmpty();
    }

    @Test
    @DisplayName("an explicit spring.datasource.url still wins")
    void doesNotOverrideAnExplicitSetting() {
        // The property source goes on last, so anything configured deliberately
        // is found first. A host that sets DATABASE_URL for something else must
        // not be able to redirect the application's own database.
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_URL", "postgres://u:p@elsewhere:5432/other",
                "spring.datasource.url", "jdbc:postgresql://chosen:5432/mine")));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://chosen:5432/mine");
        // It is still translated and still available, just outranked.
        assertThat(environment.getPropertySources()
                .contains(DatabaseUrlEnvironmentPostProcessor.SOURCE_NAME)).isTrue();
    }

    @Test
    @DisplayName("no DATABASE_URL adds nothing at all")
    void addsNothingWithoutTheVariable() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources()
                .contains(DatabaseUrlEnvironmentPostProcessor.SOURCE_NAME)).isFalse();
    }
}
