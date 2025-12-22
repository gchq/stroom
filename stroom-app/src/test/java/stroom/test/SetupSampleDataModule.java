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
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockUserSecurityContextModule;

import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;

import java.nio.file.Path;

public class SetupSampleDataModule extends AbstractModule {

    private final Config configuration;
    private final ConfigHolder configHolder;

    public SetupSampleDataModule(final Config configuration,
                                 final Path configFile) {
        this.configuration = configuration;

        configHolder = new ConfigHolder() {
            @Override
            public AppConfig getBootStrapConfig() {
                return configuration.getYamlAppConfig();
            }

            @Override
            public Path getConfigFile() {
                return configFile;
            }
        };
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        install(new BootStrapModule(configuration, new Environment("Test Environment"), configHolder.getConfigFile()));
        install(new UriFactoryModule());
        install(new CoreModule());
        install(new ResourceModule());
        install(new stroom.cluster.impl.MockClusterModule());
        install(new VolumeTestConfigModule());
        install(new MockUserSecurityContextModule());
        install(new MockMetaStatisticsModule());
        install(new stroom.test.DatabaseTestControlModule());
        install(new JerseyModule());
    }
}
