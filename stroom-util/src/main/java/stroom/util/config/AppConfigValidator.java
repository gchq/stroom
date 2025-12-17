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

package stroom.util.config;

import stroom.util.config.ConfigValidator.Result;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidationSeverity;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

public class AppConfigValidator {

    private final ConfigValidator<AbstractConfig> configValidator;

    @Inject
    public AppConfigValidator(final Validator validator) {
        this.configValidator = new ConfigValidator<>(validator, AbstractConfig.class);
    }

    public Result<AbstractConfig> validateValue(final Class<? extends AbstractConfig> configClass,
                                           final String propertyName,
                                           final Object value) {
        return configValidator.validateValue(configClass, propertyName, value, AbstractConfig.class);
    }

    public Result<AbstractConfig> validate(final AbstractConfig config) {
        return configValidator.validate(config, AbstractConfig.class);
    }

    public Result<AbstractConfig> validateRecursively(final AbstractConfig config) {
        return configValidator.validateRecursively(config, AbstractConfig.class);
    }

    public static void logConstraintViolation(final ConstraintViolation<? extends AbstractConfig> constraintViolation,
                                              final ValidationSeverity severity) {
        ConfigValidator.logConstraintViolation(constraintViolation, severity, AbstractConfig.class);
    }
}
