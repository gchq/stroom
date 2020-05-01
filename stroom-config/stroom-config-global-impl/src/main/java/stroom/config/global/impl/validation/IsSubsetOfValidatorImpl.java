package stroom.config.global.impl.validation;

import stroom.util.shared.validation.IsSubsetOf;
import stroom.util.shared.validation.IsSubsetOfValidator;

import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class IsSubsetOfValidatorImpl implements IsSubsetOfValidator {

    private List<String> allowedValues;

    @Override
    public void initialize(IsSubsetOf constraintAnnotation) {
        allowedValues = Arrays.asList(constraintAnnotation.allowedValues());
    }

    /**
     * Implements the validation logic.
     * The state of {@code value} must not be altered.
     * <p>
     * This method can be accessed concurrently, thread-safety must be ensured
     * by the implementation.
     *
     * @param values   object to validate
     * @param context context in which the constraint is evaluated
     * @return {@code false} if {@code value} does not pass the constraint
     */
    @Override
    public boolean isValid(final List<String> values,
                           final ConstraintValidatorContext context) {
        boolean result = true;

        if (values != null && !values.isEmpty()) {

            List<String> invalidValues = values.stream()
                    .filter(value -> !allowedValues.contains(value))
                    .collect(Collectors.toList());

            if (!invalidValues.isEmpty()) {
                // We want the exception details in the message so bin the default constraint
                // violation and make a new one.
                String msg = "List contains invalid values [" + String.join(",", invalidValues) + "]";
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
