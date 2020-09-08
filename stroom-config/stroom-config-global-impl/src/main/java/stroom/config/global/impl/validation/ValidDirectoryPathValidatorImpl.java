package stroom.config.global.impl.validation;

import stroom.util.shared.validation.ValidDirectoryPath;
import stroom.util.shared.validation.ValidDirectoryPathValidator;

import javax.validation.ConstraintValidatorContext;
import java.nio.file.Files;
import java.nio.file.Path;

public class ValidDirectoryPathValidatorImpl implements ValidDirectoryPathValidator {

    /**
     * Initializes the validator in preparation for
     * {@link #isValid(Object, ConstraintValidatorContext)} calls.
     * The constraint annotation for a given constraint declaration
     * is passed.
     * <p/>
     * This method is guaranteed to be called before any use of this instance for
     * validation.
     *
     * @param constraintAnnotation annotation instance for a given constraint declaration
     */
    @Override
    public void initialize(final ValidDirectoryPath constraintAnnotation) {

    }

    /**
     * Implements the validation logic.
     * The state of {@code value} must not be altered.
     * <p>
     * This method can be accessed concurrently, thread-safety must be ensured
     * by the implementation.
     *
     * @param value   object to validate
     * @param context context in which the constraint is evaluated
     * @return {@code false} if {@code value} does not pass the constraint
     */
    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        if (value != null) {
            return Files.isDirectory(Path.of(value));
        } else {
            return true;
        }
    }
}
