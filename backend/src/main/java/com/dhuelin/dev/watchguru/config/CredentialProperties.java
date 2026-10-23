package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The keys that seal third-party credentials (#39).
 *
 * @param secret      base64 of at least 32 random bytes. No default and no
 *                    generated fallback: a key invented at boot would appear to
 *                    work, then fail to open anything written before the last
 *                    restart -- and every user would silently have to
 *                    reconnect. Absent, connections that need it refuse to be
 *                    made rather than storing anything in the clear
 * @param keyId       which key this is, recorded on every row it seals. Every
 *                    new write uses this key
 * @param retiredKeys keys that no longer seal anything but may still open rows,
 *                    as {@code id:base64} pairs separated by commas. Rotation is
 *                    adding a new {@code secret} and {@code keyId}, moving the
 *                    old pair here, and waiting for the reseal job to log that
 *                    nothing is left under the old id; only then can it be
 *                    dropped. Base64 never contains {@code :} or {@code ,}, so
 *                    the format needs no escaping
 */
@ConfigurationProperties(prefix = "watch-guru.credentials")
public record CredentialProperties(
        String secret,
        @DefaultValue("primary") String keyId,
        String retiredKeys
) {
}
