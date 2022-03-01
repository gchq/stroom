package stroom.util.validation;

import stroom.util.shared.validation.IsSupersetOf;
import stroom.util.shared.validation.IsSupersetOfValidator;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.validation.ConstraintValidatorContext;

public class IsSupersetOfValidatorImpl implements IsSupersetOfValidator {

    private Set<String> requiredValues;

    @Override
    public void initialize(IsSupersetOf constraintAnnotation) {
        requiredValues = new HashSet<>(Arrays.asList(constraintAnnotation.requiredValues()));
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

            final Set<String> missingValues = new HashSet<>(requiredValues);
            missingValues.removeAll(values);

            if (!missingValues.isEmpty()) {
                // We want the exception details in the message so bin the default constraint
                // violation and make a new one.
                final String plural = missingValues.size() > 1
                        ? "s"
                        : "";
                final String msg = "Set is missing value" + plural + " ["
                        + String.join(",", missingValues)
                        + "]";
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
