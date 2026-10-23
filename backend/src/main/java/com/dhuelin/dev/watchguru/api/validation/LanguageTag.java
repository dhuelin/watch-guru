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
import java.util.regex.Pattern;

/**
 * A language in the form the metadata provider takes: an ISO 639 code,
 * optionally followed by a country or a UN M.49 area ({@code en}, {@code en-US},
 * {@code es-419}). Null is allowed.
 *
 * <p>Deliberately narrower than BCP 47. The value goes into an upstream query
 * string and into cache keys for trending and search, and anything the
 * provider would not understand is only a way to make those caches grow.
 */
@Documented
@Constraint(validatedBy = LanguageTag.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface LanguageTag {

    String message() default "must be a language such as en or en-US";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<LanguageTag, String> {

        private static final Pattern TAG = Pattern.compile("[A-Za-z]{2,3}(-([A-Za-z]{2}|[0-9]{3}))?");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || TAG.matcher(value).matches();
        }
    }
}
