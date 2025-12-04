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

package stroom.test;

import stroom.test.common.util.db.DbTestModule;
import stroom.util.shared.AbstractConfig;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.function.UnaryOperator;

@ExtendWith(GuiceExtension.class)
@IncludeModule(DbTestModule.class)
@IncludeModule(StatisticsCoreTestModule.class)
public abstract class AbstractStatisticsCoreIntegrationTest extends StroomIntegrationTest {

    @Inject
    private ConfigMapperSpy configMapperSpy;

    /**
     * Sets all the config value mappers that allow you to change the config values from their
     * defaults. Multiple calls to this will override previous calls.
     */
    public <T extends AbstractConfig> void setConfigValueMappers(final Map<Class<T>, UnaryOperator<T>> valueMappers) {
        configMapperSpy.setConfigValueMappers(valueMappers);
    }

    /**
     * Sets a single config value mapper that allows you to change the config value from its
     * defaults. Multiple call to this for the same class will override previous calls for that
     * class.
     */
    public <T extends AbstractConfig> void setConfigValueMapper(final Class<T> clazz,
                                                                final UnaryOperator<T> valueMapper) {
        configMapperSpy.setConfigValueMapper(clazz, valueMapper);
    }

    /**
     * Clears all config value mappers, thus returning to default config values.
     */
    public void clearConfigValueMapper() {
        configMapperSpy.clearConfigValueMappers();
    }
}
