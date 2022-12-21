package stroom.util.validation;

import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.shared.validation.ValidDirectoryPath;
import stroom.util.shared.validation.ValidDirectoryPathValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import javax.validation.ConstraintValidatorContext;

public class ValidDirectoryPathValidatorImpl implements ValidDirectoryPathValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidDirectoryPathValidatorImpl.class);

    private final PathCreator pathCreator;

    @Inject
    public ValidDirectoryPathValidatorImpl(final SimplePathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    /**
     * Initializes the validator in preparation for
     * {@link #isValid(String, ConstraintValidatorContext)} calls.
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
     * @param dir     object to validate
     * @param context context in which the constraint is evaluated
     * @return {@code false} if {@code value} does not pass the constraint
     */
    @Override
    public boolean isValid(final String dir, final ConstraintValidatorContext context) {
        final boolean isValid;
        if (dir != null) {
            // Use the PathCreator, so we can interpret relative paths and paths with '~' in.
            final Path modifiedDir = pathCreator.toAppPath(dir);

            LOGGER.debug("Validating dir {} (modified to {})", dir, modifiedDir);
            final Path path = modifiedDir;
            isValid = Files.isDirectory(path) && Files.isReadable(path);
            if (!isValid) {
                String msg = context.getDefaultConstraintMessageTemplate();
                if (!modifiedDir.toString().equals(dir)) {
                    msg += " (as absolute path: " + modifiedDir + ")";
                }
                context.disableDefaultConstraintViolation();
                context
                        .buildConstraintViolationWithTemplate(msg)
                        .addConstraintViolation();
            }
        } else {
            isValid = true;
        }
        return isValid;
    }
}
