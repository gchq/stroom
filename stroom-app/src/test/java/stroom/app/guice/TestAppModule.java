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

package stroom.app.guice;

import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.guice.GuiceTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class TestAppModule extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAppModule.class);

    @Disabled // manual test to dump the guice modules/bindings tree. Needs a db connection
    @Test
    void dumpAppModuleTree() {
        final BootStrapModule bootStrapModule = getBootStrapModule();

        final String bootStrapDump = GuiceTestUtil.dumpGuiceModuleHierarchy(bootStrapModule);
        LOGGER.info("bootStrap:\n{}", bootStrapDump);

        // Stroom use the bootstrap module's injector to create a child injector with the AppModule
        final AbstractModule combinedModule = getCombinedModule(bootStrapModule);
        final String appDump = GuiceTestUtil.dumpGuiceModuleHierarchy(combinedModule);
        LOGGER.info("app:\n{}", appDump);
    }

    @Disabled // manual test to dump the guice binds sorted by key
    @Test
    void dumpAppModuleBindKeys() {
        final BootStrapModule bootStrapModule = getBootStrapModule();
        final String bootStrapDump = GuiceTestUtil.dumpBindsSortedByKey(bootStrapModule);
        LOGGER.info("bootStrap:\n{}", bootStrapDump);

        final AbstractModule combinedModule = getCombinedModule(bootStrapModule);

        final String appDump = GuiceTestUtil.dumpBindsSortedByKey(combinedModule);
        LOGGER.info("app:\n{}", appDump);
    }

    @NotNull
    private BootStrapModule getBootStrapModule() {
        final Config config = new Config(new AppConfig());
        return new BootStrapModule(
                config,
                new Environment("Test Environment"),
                Path.of("DUMMY"));
    }

    @NotNull
    private AbstractModule getCombinedModule(final BootStrapModule bootStrapModule) {
        // Stroom use the bootstrap module's injector to create a child injector with the AppModule
        final AppModule appModule = new AppModule();
        return new AbstractModule() {
            @Override
            protected void configure() {
                install(bootStrapModule);
                install(appModule);
            }
        };
    }
}
