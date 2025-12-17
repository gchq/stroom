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
import stroom.util.shared.validation.ValidFilePath;
import stroom.util.shared.validation.ValidFilePathValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class ValidFilePathValidatorImpl implements ValidFilePathValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidFilePathValidatorImpl.class);

    private final PathCreator pathCreator;

    @Inject
    public ValidFilePathValidatorImpl(final SimplePathCreator pathCreator) {
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
    public void initialize(final ValidFilePath constraintAnnotation) {

    }

    /**
     * Implements the validation logic.
     * The state of {@code value} must not be altered.
     * <p>
     * This method can be accessed concurrently, thread-safety must be ensured
     * by the implementation.
     *
     * @param file    object to validate
     * @param context context in which the constraint is evaluated
     * @return {@code false} if {@code value} does not pass the constraint
     */
    @Override
    public boolean isValid(final String file, final ConstraintValidatorContext context) {
        final boolean isValid;
        if (file != null) {
            // Use the PathCreator so we can interpret relative paths and paths with '~' in.
            final Path modifiedFile = pathCreator.toAppPath(file);

            LOGGER.debug("Validating file {} (modified to {})", file, modifiedFile);
            final Path path = modifiedFile;
            isValid = Files.isRegularFile(path) && Files.isReadable(path);
            if (!isValid) {
                String msg = context.getDefaultConstraintMessageTemplate();
                if (!modifiedFile.toString().equals(file)) {
                    msg += " (as absolute path: " + modifiedFile + ")";
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
