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

package stroom.proxy.app;

import stroom.util.config.ConfigValidator;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.ValidationSeverity;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

public class ProxyConfigValidator {

    private final ConfigValidator<IsProxyConfig> configValidator;

    @Inject
    public ProxyConfigValidator(final Validator validator) {
        this.configValidator = new ConfigValidator<>(validator, IsProxyConfig.class);
    }

    public Result<IsProxyConfig> validateValue(final Class<? extends IsProxyConfig> configClass,
                                               final String propertyName,
                                               final Object value) {
        return configValidator.validateValue(configClass, propertyName, value, IsProxyConfig.class);
    }

    public Result<IsProxyConfig> validate(final IsProxyConfig config) {
        return configValidator.validate(config, IsProxyConfig.class);
    }

    public Result<IsProxyConfig> validateRecursively(final IsProxyConfig config) {
        return configValidator.validateRecursively(config, IsProxyConfig.class);
    }

    public static void logConstraintViolation(final ConstraintViolation<? extends IsProxyConfig> constraintViolation,
                                              final ValidationSeverity severity) {
        ConfigValidator.logConstraintViolation(constraintViolation, severity, IsProxyConfig.class);
    }
}
