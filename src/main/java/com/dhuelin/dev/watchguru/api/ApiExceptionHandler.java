package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.MetadataProviderNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps application exceptions onto RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail onNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detail.isBlank() ? "Request validation failed" : detail);
    }

    /**
     * Missing provider configuration is our fault, not the provider's, so it is
     * reported as 503 rather than 502.
     */
    @ExceptionHandler(MetadataProviderNotConfiguredException.class)
    ProblemDetail onProviderNotConfigured(MetadataProviderNotConfiguredException e) {
        log.error("Metadata provider is not configured: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /**
     * Upstream metadata failures are reported as 502: the request was valid but
     * the provider could not answer it.
     */
    @ExceptionHandler(MetadataProviderException.class)
    ProblemDetail onProviderFailure(MetadataProviderException e) {
        log.warn("Metadata provider failure: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
