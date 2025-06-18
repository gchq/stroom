package stroom.util.validation;

import stroom.util.shared.validation.AllMatchPattern;
import stroom.util.shared.validation.AllMatchPatternValidator;

import jakarta.validation.ConstraintValidatorContext;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AllMatchPatternValidatorImpl implements AllMatchPatternValidator {

    private Pattern pattern;

    @Override
    public void initialize(final AllMatchPattern constraintAnnotation) {
        pattern = Pattern.compile(constraintAnnotation.pattern());
    }

    /**
     * Implements the validation logic.
     * The state of {@code value} must not be altered.
     * <p>
     * This method can be accessed concurrently, thread-safety must be ensured
     * by the implementation.
     *
     * @param values  object to validate
     * @param context context in which the constraint is evaluated
     * @return {@code false} if {@code value} does not pass the constraint
     */
    @Override
    public boolean isValid(final Collection<String> values,
                           final ConstraintValidatorContext context) {
        boolean result = true;

        if (values != null && !values.isEmpty()) {
            final Predicate<String> regexMatchPredicate = pattern.asMatchPredicate();
            final List<String> invalidValues = values.stream()
                    .filter(str ->
                            str == null || !regexMatchPredicate.test(str))
                    .collect(Collectors.toList());

            if (!invalidValues.isEmpty()) {
                // We want the exception details in the message so bin the default constraint
                // violation and make a new one.
                final int count = invalidValues.size();
                final String msg = "Set contains "
                        + count
                        + (count > 1
                        ? " values that do"
                        : " value that does")
                        + " not match pattern '" + pattern.toString() + "', "
                        + "invalid value" + (count > 1
                        ? "s"
                        : "")
                        + ": ["
                        + String.join(",", invalidValues) + "]";

                context.disableDefaultConstraintViolation();
                context
                        .buildConstraintViolationWithTemplate(msg)
                        .addConstraintViolation();
                result = false;
            }
        }
        return result;
    }
}
