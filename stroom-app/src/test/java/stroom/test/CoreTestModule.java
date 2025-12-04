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

import stroom.app.guice.CoreModule;
import stroom.app.guice.DbConnectionsModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.config.global.impl.GlobalConfigBootstrapModule;
import stroom.config.global.impl.db.GlobalConfigDaoModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockUserSecurityContextModule;
import stroom.test.common.MockMetricsModule;
import stroom.util.io.DirProvidersModule;

import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;

public class CoreTestModule extends AbstractModule {

    @Override
    protected void configure() {
        // Stuff that comes from BootStrapModule
        bind(Environment.class).toInstance(new Environment("TestEnvironment"));
        install(new GlobalConfigBootstrapModule());
        install(new GlobalConfigDaoModule());
        install(new DirProvidersModule());
        install(new DbConnectionsModule());

        install(new AppConfigTestModule());
        install(new UriFactoryModule());
        install(new MockMetricsModule());
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
