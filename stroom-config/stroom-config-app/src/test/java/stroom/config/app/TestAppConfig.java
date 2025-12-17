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

package stroom.config.app;

import stroom.docref.DocRef;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TestAppConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAppConfig.class);

    private static final Set<Class<?>> WHITE_LISTED_CLASSES = Set.of(
            Logger.class,
            LambdaLogger.class,
            StroomDuration.class
    );

    /**
     * Test to verify that all fields in the config tree of type stroom.*
     * extend AbstractConfig . Also useful for seeing the object tree
     * and the annotations
     */
    @Test
    public void testAbstractConfigUse() {
        checkProperties(AppConfig.class, "");
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

                Assertions.assertThat(AbstractConfig.class)
                        .withFailMessage(LogUtil.message("Class {} does not extend {}",
                                fieldClass.getName(),
                                AbstractConfig.class.getName()))
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
                new AppConfig(),
                prop -> true,
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
                new AppConfig(),
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

    @Test
    void testPropertyDecoration() {
        final AppConfig appConfig = new AppConfig();

        LOGGER.logDurationIfInfoEnabled(
                () -> {
                    for (int i = 0; i < 100; i++) {
                        PropertyPathDecorator.decoratePaths(
                                appConfig, AppConfig.ROOT_PROPERTY_PATH);
                    }
                },
                "decorating paths");

        Assertions.assertThat(appConfig.getActivityConfig()
                .getDbConfig()
                .getConnectionConfig()
                .getBasePathStr())
                .isEqualTo("stroom.activity.db.connection");
    }

    private boolean isCollectionClass(final Class<?> clazz) {
        return clazz.isAssignableFrom(List.class)
                || clazz.isAssignableFrom(Map.class)
                || clazz.equals(DocRef.class);
    }

}
