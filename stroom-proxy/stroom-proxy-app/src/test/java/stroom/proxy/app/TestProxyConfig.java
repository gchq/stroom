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

import stroom.docref.DocRef;
import stroom.test.common.util.test.TestingHomeAndTempProvidersModule;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.config.ConfigValidator.Result;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;
import stroom.util.validation.ValidationModule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TestProxyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProxyConfig.class);

    private static final Set<Class<?>> WHITE_LISTED_CLASSES = Set.of(
            Logger.class,
            LambdaLogger.class,
            StroomDuration.class
    );

    @Test
    void testValidation(@TempDir final Path tempDir) throws IOException {

        final TestingHomeAndTempProvidersModule testingHomeAndTempProvidersModule =
                new TestingHomeAndTempProvidersModule(tempDir);

        final Injector injector = Guice.createInjector(
                testingHomeAndTempProvidersModule,
                new ValidationModule());

        final ProxyConfigValidator proxyConfigValidator = injector.getInstance(ProxyConfigValidator.class);

        final ProxyConfig vanillaAppConfig = new ProxyConfig();

        final ProxyPathConfig modifiedPathConfig = vanillaAppConfig.getPathConfig()
                .withHome(testingHomeAndTempProvidersModule.getHomeDir().toAbsolutePath().toString())
                .withTemp(tempDir.toAbsolutePath().toString());

        final ProxyConfig proxyConfig = AbstractConfigUtil.mutateTree(
                vanillaAppConfig,
                ProxyConfig.ROOT_PROPERTY_PATH,
                Map.of(ProxyConfig.ROOT_PROPERTY_PATH.merge(ProxyConfig.PROP_NAME_PATH), modifiedPathConfig));

        // create the dirs so they validate ok
        Files.createDirectories(tempDir);
        Files.createDirectories(testingHomeAndTempProvidersModule.getHomeDir());
        Files.createDirectories(testingHomeAndTempProvidersModule.getHomeDir()
                .resolve(proxyConfig.getPathConfig().getData()));

        final Result<IsProxyConfig> result = proxyConfigValidator.validateRecursively(proxyConfig);

        result.handleViolations(ProxyConfigValidator::logConstraintViolation);

        Assertions.assertThat(result.hasErrorsOrWarnings())
                .isFalse();
    }

    /**
     * Test to verify that all fields in the config tree of type stroom.*
     * implement IsProxyConfig . Also useful for seeing the object tree
     * and the annotations
     */
    @Test
    public void testIsProxyConfigUse() {
        checkProperties(ProxyConfig.class, "");
    }

    private void checkProperties(final Class<?> clazz, final String indent) {
        for (final Field field : clazz.getDeclaredFields()) {
            final Class<?> fieldClass = field.getType();

            // We are trying to inspect props that are themselves config objects
            if (fieldClass.getName().startsWith("stroom")
                    && fieldClass.getSimpleName().endsWith("Config")
                    && !WHITE_LISTED_CLASSES.contains(fieldClass)) {

                LOGGER.debug("{}Field {} : {} {}",
                        indent, field.getName(), fieldClass.getSimpleName(), fieldClass.getAnnotations());

                Assertions.assertThat(IsProxyConfig.class)
                        .withFailMessage(LogUtil.message("Class {} does not extend {}",
                                fieldClass.getName(),
                                IsProxyConfig.class.getName()))
                        .isAssignableFrom(fieldClass);

                // This field is another config object so recurs into it
                checkProperties(fieldClass, indent + "  ");
            } else {
                // Not a stroom config object so nothing to do
            }
        }
    }

    @Test
    void showPropsWithNullValues() {
        // list any config values that are null.  This may be valid so no assertions used.
        PropertyUtil.walkObjectTree(
                new ProxyConfig(),
                prop -> !prop.hasAnnotation(JsonIgnore.class),
                prop -> {
                    if (prop.getValueFromConfigObject() == null) {
                        LOGGER.warn("{} => {} is null",
                                prop.getParentObject().getClass().getSimpleName(),
                                prop.getName());
                    }
                });
    }

    @Test
    void showPropsWithCollectionValues() {
        // list any config values that are null.  This may be valid so no assertions used.
        PropertyUtil.walkObjectTree(
                new ProxyConfig(),
                prop -> true,
                prop -> {
                    final Class<?> valueClass = prop.getValueClass();
                    if (!valueClass.getName().startsWith("stroom")
                            && isCollectionClass(valueClass)) {
                        LOGGER.warn("{}.{} => {} => {}",
                                prop.getParentObject().getClass().getSimpleName(),
                                prop.getName(),
                                prop.getValueType(),
                                prop.getValueClass());
                    }
//                    if (prop.getValueType().getTypeName().matches("")) {
//                    }
                });
    }

    private boolean isCollectionClass(final Class<?> clazz) {
        return clazz.isAssignableFrom(List.class)
                || clazz.isAssignableFrom(Map.class)
                || clazz.equals(DocRef.class);
    }
}
