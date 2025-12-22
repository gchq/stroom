/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.validation;

import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.shared.validation.ValidDirectoryPath;
import stroom.util.shared.validation.ValidDirectoryPathValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ValidDirectoryPathValidatorImpl implements ValidDirectoryPathValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidDirectoryPathValidatorImpl.class);

    private final PathCreator pathCreator;
    private boolean ensureExistence = false;

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
        this.ensureExistence = constraintAnnotation.ensureExistence();
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
        if (dir != null && !dir.isBlank()) {
            // Use the PathCreator, so we can interpret relative paths and paths with '~' in.
            final Path modifiedDir = pathCreator.toAppPath(dir);

            LOGGER.debug("Validating dir {} (modified to {})", dir, modifiedDir);
            boolean exists = Files.exists(modifiedDir);
            if (!exists && ensureExistence) {
                try {
                    LOGGER.info("Creating dir {} (modified to {})", dir, modifiedDir);
                    Files.createDirectories(modifiedDir);
                } catch (final IOException e) {
                    final String msg = "error creating directory: " + e.getMessage();
                    addValidationMessage(context, dir, modifiedDir, msg);
                }
            }
            exists = Files.exists(modifiedDir);

            if (!exists) {
                final String msg = "path does not exist";
                addValidationMessage(context, dir, modifiedDir, msg);
                isValid = false;
            } else if (!Files.isDirectory(modifiedDir)) {
                final String msg = "path is not a directory";
                addValidationMessage(context, dir, modifiedDir, msg);
                isValid = false;
            } else if (!Files.isWritable(modifiedDir)) {
                final String msg = "directory is not writable";
                addValidationMessage(context, dir, modifiedDir, msg);
                isValid = false;
            } else {
                isValid = true;
            }
        } else {
            isValid = true;
        }
        return isValid;
    }

    private void addValidationMessage(final ConstraintValidatorContext context,
                                      final String configValue,
                                      final Path path,
                                      final String msg) {
        String msg2 = msg;
        if (!path.toString().equals(configValue)) {
            msg2 += " (as absolute path: " + path + ")";
        }
        context.disableDefaultConstraintViolation();
        context
                .buildConstraintViolationWithTemplate(msg2)
                .addConstraintViolation();
    }
}
