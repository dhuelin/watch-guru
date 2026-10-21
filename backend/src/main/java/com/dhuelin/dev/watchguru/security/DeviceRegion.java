package com.dhuelin.dev.watchguru.security;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The country the calling device says it is in, read once at sign-up.
 *
 * <p>The apps send it as {@value #HEADER} on every request, taken from the
 * device's locale. It is only ever used to seed a brand-new account's region:
 * without it every new user started out on the configured default and was
 * shown another country's streaming offers until they found the setting in
 * Profile. An existing account's region is the user's own choice and is never
 * touched by this.
 *
 * <p>Anything that is not an ISO 3166-1 alpha-2 code is ignored rather than
 * rejected. Locales legitimately carry no country ({@code en}) or a UN M.49
 * area ({@code es-419}), and neither is a reason to fail a sign-in.
 */
public final class DeviceRegion {

    public static final String HEADER = "X-Device-Region";

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private DeviceRegion() {
    }

    /** The header on the request being handled, if there is one and it is a country. */
    public static Optional<String> ofCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        return parse(servlet.getRequest().getHeader(HEADER));
    }

    /** {@code value} as an upper-case ISO 3166-1 alpha-2 code, or empty. */
    static Optional<String> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        return COUNTRIES.contains(code) ? Optional.of(code) : Optional.empty();
    }
}
