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

import stroom.app.guice.BootStrapModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StroomPathConfig;

import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class BootstrapTestModule extends AbstractModule {

    private final BootStrapModule bootStrapModule;

//    private BootstrapTestModule(final Config config, final ConfigHolder configHolder) {
//        super(config, null, configHolder, DbTestModule::new, AppConfigTestModule::new);
//
//
//        final ConfigHolder configHolder = new ConfigHolderImpl();
//        final Config config = new Config(configHolder.getBootStrapConfig());
//
//        // Delegate to the normal BootStrapModule but use different Db and AppConfig modules
//        bootStrapModule = new BootStrapModule(
//                config,
//                null,
//                configHolder,
//                DbTestModule::new,
//                AppConfigTestModule::new);

//        super(new Config(new ConfigHolderImpl()), null, Path.of("Dummy"),
//        DbTestModule::new, AppConfigTestModule::new);
//        config.setYamlAppConfig(configHolder.getBootStrapConfig());
//    }

    public BootstrapTestModule() {
        final ConfigHolder configHolder = new ConfigHolderImpl();
        final Config config = new Config(configHolder.getBootStrapConfig());
        final Environment environment = new Environment("TestEnvironment");

        // Delegate to the normal BootStrapModule but use different Db and AppConfig modules
        bootStrapModule = new BootStrapModule(
                config,
                environment,
                configHolder,
                DbTestModule::new,
                AppConfigTestModule::new);
    }

//    public static BootstrapTestModule create() {
//        final ConfigHolder configHolder = new ConfigHolderImpl();
//        final Config config = new Config(configHolder.getBootStrapConfig());
//
//        return new BootstrapTestModule(config, configHolder);
//    }

    @Override
    protected void configure() {
        super.configure();

        install(bootStrapModule);
//        bootStrapModule.configure();
//
//        bind(Config.class).toInstance(config);
//
//        install(new AppConfigTestModule(configHolder));
//
//        install(new DbTestModule());
//        install(new DbConnectionsModule());
//
//        // Any DAO/Service modules that we must have
//        install(new GlobalConfigBootstrapModule());
//        install(new GlobalConfigDaoModule());
//        install(new DirProvidersModule());
    }

    private static class ConfigHolderImpl implements ConfigHolder {

        private final Config config;
        private final AppConfig appConfig;
        private final Path path;

        ConfigHolderImpl() {
            try {
                final String gradleWorker = DbTestUtil.getGradleWorker();
                final String prefix = "stroom_" + gradleWorker + "_";
                final Path dir = Files.createTempDirectory(prefix);
                this.path = dir.resolve("test.yml");

                final AppConfig vanillaAppConfig = new AppConfig();

                final StroomPathConfig modifiedPathConfig = vanillaAppConfig.getPathConfig()
                        .withHome(FileUtil.getCanonicalPath(dir))
                        .withTemp(FileUtil.getCanonicalPath(dir));

                final AppConfig modifiedAppConfig = AbstractConfigUtil.mutateTree(
                        vanillaAppConfig,
                        AppConfig.ROOT_PROPERTY_PATH,
                        Map.of(AppConfig.ROOT_PROPERTY_PATH.merge(AppConfig.PROP_NAME_PATH), modifiedPathConfig));

                this.appConfig = modifiedAppConfig;
                this.config = new Config();
                this.config.setYamlAppConfig(appConfig);
            } catch (final IOException e) {
                throw new UncheckedIOException(e.getMessage(), e);
            }
        }

        @Override
        public AppConfig getBootStrapConfig() {
            return appConfig;
        }

        @Override
        public Path getConfigFile() {
            return path;
        }

        public Config getConfig() {
            return config;
        }
    }
}
