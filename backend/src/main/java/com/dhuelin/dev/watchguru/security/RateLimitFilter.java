package com.dhuelin.dev.watchguru.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-caller request budgets.
 *
 * <p>Not about passwords -- there are none, and every token here is 256 bits.
 * It is about the endpoints that spend something shared: search, trending and
 * import previews each cost TMDB calls on the service's one API key, and one
 * account looping any of them would collect the 429s that then open the
 * circuit breaker for everybody. Sign-in and the webhooks are unauthenticated
 * to the filter chain, so they are budgeted by address instead.
 *
 * <p>Fixed one-minute windows, in memory, per instance. Coarse on purpose: the
 * point is to make a loop expensive, not to meter honest use, and the limits
 * sit well above what either app does on its busiest screen. Behind several
 * replicas each one counts separately, which loosens the limit without
 * breaking anything.
 *
 * <p>Ordered just inside Spring Security's filter chain, so a bearer token has
 * already been verified and the caller can be keyed by who they are rather
 * than by an address a phone changes every time it moves between networks.
 */
@Component
@Order(RateLimitFilter.ORDER)
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * One after Spring Security's own filter, which Spring Boot registers at
     * -100 ({@code SecurityProperties.DEFAULT_FILTER_ORDER}).
     */
    static final int ORDER = -99;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** First match wins, so the specific rules come before the catch-all. */
    private static final List<Rule> RULES = List.of(
            new Rule("auth", HttpMethod.POST, "/api/v1/auth/", 20),
            new Rule("webhook", HttpMethod.POST, "/api/v1/webhooks/", 120),
            new Rule("import-preview", HttpMethod.POST, "/api/v1/me/imports/preview", 5),
            new Rule("search", HttpMethod.GET, "/api/v1/titles/search", 60),
            new Rule("trending", HttpMethod.GET, "/api/v1/titles/trending", 60),
            new Rule("title-import", HttpMethod.POST, "/api/v1/titles/import", 60),
            new Rule("api", null, "/api/", 600));

    private final Clock clock;
    private final boolean enabled;
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(WINDOW.multipliedBy(2))
            .maximumSize(100_000)
            .build();

    @Autowired
    public RateLimitFilter(ObjectProvider<Clock> clock,
                           @Value("${watch-guru.rate-limit.enabled:true}") boolean enabled) {
        // Optional because web-layer test slices load filters without the
        // application's configuration classes.
        this(clock.getIfAvailable(Clock::systemUTC), enabled);
    }

    RateLimitFilter(Clock clock, boolean enabled) {
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = ruleFor(request.getMethod(), request.getRequestURI());
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        long nowMillis = clock.millis();
        long window = nowMillis / WINDOW.toMillis();
        String key = rule.name() + "|" + callerKey(request) + "|" + window;
        int count = counters.get(key, k -> new AtomicInteger()).incrementAndGet();

        if (count > rule.perMinute()) {
            long retryAfter = Math.max(1, ((window + 1) * WINDOW.toMillis() - nowMillis + 999) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,\
                    "detail":"Too many requests. Please wait a moment and try again."}""");
            return;
        }
        chain.doFilter(request, response);
    }

    static Rule ruleFor(String method, String path) {
        for (Rule rule : RULES) {
            if ((rule.method() == null || rule.method().matches(method)) && path.startsWith(rule.pathPrefix())) {
                return rule;
            }
        }
        return null;
    }

    /**
     * The verified token's subject when there is one, the client address
     * otherwise. The address is the forwarded one: Fly's proxy sets it, and
     * {@code server.forward-headers-strategy} makes the container believe it
     * only from Fly's own private addresses.
     */
    private static String callerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && auth.isAuthenticated()) {
            return "u:" + CurrentUserService.namespacedSubject(jwt.getToken());
        }
        return "ip:" + request.getRemoteAddr();
    }

    record Rule(String name, HttpMethod method, String pathPrefix, int perMinute) {
    }
}
