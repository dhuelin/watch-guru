package com.dhuelin.dev.watchguru.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.Set;

/**
 * An ISO 3166-1 alpha-2 country, in either case; null is allowed.
 *
 * <p>Region is stored on the account and passed to the metadata provider, and
 * availability is cached per region. A free-form string would let one caller
 * fill that cache with keys nobody else will ever ask for, and would store
 * whatever it was sent in a column every later request reads.
 */
@Documented
@Constraint(validatedBy = CountryCode.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CountryCode {

    String message() default "must be an ISO 3166-1 alpha-2 country code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CountryCode, String> {

        private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || COUNTRIES.contains(value.toUpperCase(Locale.ROOT));
        }
    }
}
