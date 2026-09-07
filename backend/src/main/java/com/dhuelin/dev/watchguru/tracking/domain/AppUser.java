package com.dhuelin.dev.watchguru.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OIDC subject, once real authentication is wired up. */
    @Column(name = "auth_subject")
    private String authSubject;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /** ISO 3166-1 country code; decides which streaming offers are shown. */
    @Column(name = "region", nullable = false, length = 2)
    private String region = "US";

    @Column(name = "language", nullable = false, length = 16)
    private String language = "en-US";

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "UTC";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public AppUser(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    /** Resolved zone for day-bucketing stats; falls back to UTC if unparseable. */
    public ZoneId zone() {
        try {
            return ZoneId.of(timeZone);
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
