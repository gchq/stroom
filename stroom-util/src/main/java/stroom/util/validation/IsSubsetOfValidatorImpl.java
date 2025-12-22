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

import stroom.util.shared.validation.IsSubsetOf;
import stroom.util.shared.validation.IsSubsetOfValidator;

import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class IsSubsetOfValidatorImpl implements IsSubsetOfValidator {

    private Set<String> allowedValues;

    @Override
    public void initialize(final IsSubsetOf constraintAnnotation) {
        allowedValues = new HashSet<>(Arrays.asList(constraintAnnotation.allowedValues()));
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

            final Set<String> invalidValues = new HashSet<>(values);
            invalidValues.removeAll(allowedValues);

            if (!invalidValues.isEmpty()) {
                // We want the exception details in the message so bin the default constraint
                // violation and make a new one.
                final String plural = invalidValues.size() > 1
                        ? "s"
                        : "";
                final String msg = "Set contains invalid value"
                        + plural
                        + " ["
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
