package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The key that seals third-party credentials (#39).
 *
 * @param secret base64 of at least 32 random bytes. No default and no
 *               generated fallback: a key invented at boot would appear to
 *               work, then fail to open anything written before the last
 *               restart -- and every user would silently have to reconnect.
 *               Absent, connections that need it refuse to be made rather than
 *               storing anything in the clear
 * @param keyId  which key this is, recorded on every row it seals. Rotation is
 *               not implemented, but telling a row sealed under a retired key
 *               from a corrupt one is the difference between a fixable problem
 *               and a mystery
 */
@ConfigurationProperties(prefix = "watch-guru.credentials")
public record CredentialProperties(
        String secret,
        @DefaultValue("primary") String keyId
) {
}
