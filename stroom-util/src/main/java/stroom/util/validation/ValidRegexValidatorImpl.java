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

import stroom.util.shared.validation.ValidRegex;
import stroom.util.shared.validation.ValidRegexValidator;

import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ValidRegexValidatorImpl implements ValidRegexValidator {

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
    public void initialize(final ValidRegex constraintAnnotation) {

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
        boolean result = true;

        if (value != null) {
            try {
                Pattern.compile(value);
            } catch (final PatternSyntaxException e) {
                final String msgTemplate =
                        context.getDefaultConstraintMessageTemplate() +
                                ". caused by: " +
                                e.getMessage().replaceAll("\\n", " ");

                // We want the exception details in the message so bin the default constraint
                // violation and make a new one.
                context.disableDefaultConstraintViolation();
                context
                        .buildConstraintViolationWithTemplate(msgTemplate)
                        .addConstraintViolation();
                result = false;
            }
        }
        return result;
    }
}
