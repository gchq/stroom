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

package stroom.test.common;

import stroom.test.common.util.test.TestingHomeAndTempProvidersModule;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.validation.ValidationModule;

import com.google.inject.Guice;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

public abstract class AbstractValidatorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractValidatorTest.class);

    @TempDir
    Path tempDir;

    public Validator getValidator() {

        return Guice.createInjector(
                new TestingHomeAndTempProvidersModule(tempDir),
                new ValidationModule()
        ).getInstance(Validator.class);
    }

    public <T> Set<ConstraintViolation<T>> validate(final T object) {
        return getValidator().validate(object);
    }

    public <T> Set<ConstraintViolation<T>> validateValidValue(final T object) {
        final Set<ConstraintViolation<T>> violations = getValidator().validate(object);
        Assertions.assertThat(violations)
                .isEmpty();
        return violations;
    }

    public <T> Set<ConstraintViolation<T>> validateInvalidValue(final T object) {
        final Set<ConstraintViolation<T>> violations = getValidator().validate(object);
        Assertions.assertThat(violations)
                .hasSize(1);

        LOGGER.debug(() -> "Message: " + violations.iterator().next().getMessage());

        return violations;
    }
}
