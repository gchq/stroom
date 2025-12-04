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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.validation.AllMatchPatternValidator;
import stroom.util.shared.validation.IsSubsetOfValidator;
import stroom.util.shared.validation.IsSupersetOfValidator;
import stroom.util.shared.validation.ValidDirectoryPathValidator;
import stroom.util.shared.validation.ValidFilePathValidator;
import stroom.util.shared.validation.ValidRegexValidator;
import stroom.util.shared.validation.ValidSimpleCronValidator;

import com.google.inject.ConfigurationException;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;

import java.util.Map;

@Singleton
public class CustomConstraintValidatorFactory implements ConstraintValidatorFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CustomConstraintValidatorFactory.class);

    // iface class => validator provider
    // Use providers so we don't have to permanently hold all the validators when
    // not in use.
    private final Map<Class<?>, Object> validatorProviderMap;

    private final ConstraintValidatorFactory delegate;
//    private final StroomValidatorProvider stroomValidatorProvider;

    // We can't just inject the Injector due to Guice bug https://github.com/google/guice/issues/973
    // Instead we have to explicitly inject each of our validators
    @Inject
    CustomConstraintValidatorFactory(
            final Provider<IsSubsetOfValidator> isSubsetOfValidatorProvider,
            final Provider<IsSupersetOfValidator> isSupersetOfValidatorProvider,
            final Provider<ValidDirectoryPathValidator> validDirectoryPathValidatorProvider,
            final Provider<ValidFilePathValidator> validFilePathValidatorProvider,
            final Provider<ValidRegexValidator> validRegexValidatorProvider,
            final Provider<ValidSimpleCronValidator> validSimpleCronValidatorProvider,
            final Provider<AllMatchPatternValidator> allMatchPatternValidatorProvider) {

        // TODO: 28/09/2023 I'm sure we could just use a map binder
        validatorProviderMap = Map.of(
                IsSubsetOfValidator.class, isSubsetOfValidatorProvider,
                IsSupersetOfValidator.class, isSupersetOfValidatorProvider,
                ValidDirectoryPathValidator.class, validDirectoryPathValidatorProvider,
                ValidFilePathValidator.class, validFilePathValidatorProvider,
                ValidRegexValidator.class, validRegexValidatorProvider,
                ValidSimpleCronValidator.class, validSimpleCronValidatorProvider,
                AllMatchPatternValidator.class, allMatchPatternValidatorProvider);

        this.delegate = Validation.byDefaultProvider()
                .configure()
                .getDefaultConstraintValidatorFactory();

        LOGGER.debug(() -> LogUtil.message("Using validation provider {}", delegate.getClass().getName()));
    }

    /**
     * @param key The class of the constraint validator to instantiate
     * @return A new constraint validator instance of the specified class
     */
    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(final Class<T> key) {
        final T validator;

        // See if guice has the validator class we are after, i.e. one of our custom validators
        if (key.getCanonicalName().startsWith("stroom.")) {
            try {
                validator = getStroomValidatorInstance(key);
                LOGGER.debug(() -> LogUtil.message("Obtained class {} from Guice injector", key.getName()));
            } catch (final ConfigurationException e) {
                throw new RuntimeException(LogUtil.message(
                        "Error getting instance of {} from Guice", key.getCanonicalName(), e));
            }
        } else {
            // Not one of ours so delegate
            validator = delegate.getInstance(key);
            LOGGER.debug(() ->
                    LogUtil.message("Obtained class {} from {}", key.getName(), delegate.getClass().getName()));
        }
        return validator;
    }

    private <T> T getStroomValidatorInstance(final Class<T> clazz) {
        final Object validatorProvider = validatorProviderMap.get(clazz);
        if (validatorProvider == null) {
            throw new RuntimeException("No provider found for validator class " + clazz);
        }

        final Object validator = ((Provider<?>) validatorProvider).get();

        if (validator == null) {
            throw new RuntimeException("Null validator for validator class " + clazz);
        }

        if (!clazz.isAssignableFrom(validator.getClass())) {
            throw new RuntimeException(LogUtil.message("Unexpected class {} for key {}",
                    validator.getClass(), clazz));
        }
        return (T) validator;
    }

    /**
     * Signals {@code ConstraintValidatorFactory} that the instance is no longer
     * being used by the Bean Validation provider.
     *
     * @param instance validator being released
     * @since 1.1
     */
    @Override
    public void releaseInstance(final ConstraintValidator<?, ?> instance) {
        if (!instance.getClass().getCanonicalName().startsWith("stroom.")) {
            // Not one of ours so pass to delegate
            delegate.releaseInstance(instance);
        }
        // A guice bound validator should go for garbage collection once all references are gone.
    }
}
