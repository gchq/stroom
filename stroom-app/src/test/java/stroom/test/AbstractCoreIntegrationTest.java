/*
 * Copyright 2016 Crown Copyright
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

import stroom.lmdb.LmdbLibraryConfig;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;

import jakarta.inject.Inject;
import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;

@ExtendWith(GuiceExtension.class)
@IncludeModule(DbTestModule.class)
@IncludeModule(CoreTestModule.class)
public abstract class AbstractCoreIntegrationTest extends StroomIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractCoreIntegrationTest.class);

    @Inject
    private ConfigMapperSpy configMapperSpy;

    // This is all a bit nasty, but we need to be able to set a config mapper before the
    // guice bindings happen as the lmdb lib path is read by eager singletons before we
    // can inject and use ConfigMapperSpy.
    static {
        final Path libPath;
        try {
            libPath = Files.createTempDirectory("stroom_lmdb_lib__");
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error creating temp directory for LMDB library. {}", e.getMessage()), e);
        }
        final String libPathStr = libPath.toAbsolutePath().toString();
        LOGGER.info("Configuring LMDB lib location to {}", libPathStr);
        ConfigMapperSpy.setStaticConfigValueMapper(LmdbLibraryConfig.class, lmdbLibraryConfig ->
                lmdbLibraryConfig.withSystemLibraryExtractDir(libPathStr));
    }

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
