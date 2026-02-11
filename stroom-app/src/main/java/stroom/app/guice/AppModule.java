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

import stroom.app.AdminAccountBootstrap;
import stroom.app.metrics.StroomAppInfoProvider;
import stroom.app.uri.UriFactoryModule;
import stroom.cluster.impl.ClusterModule;
import stroom.dropwizard.common.FilteredHealthCheckServlet;
import stroom.dropwizard.common.LogLevelInspector;
import stroom.dropwizard.common.prometheus.AppInfoProvider;
import stroom.dropwizard.common.prometheus.PrometheusModule;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.lifecycle.impl.LifecycleServiceModule;
import stroom.meta.statistics.impl.MetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.impl.SecurityContextModule;
import stroom.statistics.impl.sql.search.SQLStatisticSearchModule;
import stroom.util.RunnableWrapper;
import stroom.util.guice.AdminServletBinder;
import stroom.util.guice.HasSystemInfoBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class AppModule extends AbstractModule {

    @Override
    protected void configure() {

        install(new UriFactoryModule());
        install(new CoreModule());
        install(new stroom.langchain.impl.OpenAIModule());
        install(new LifecycleServiceModule());
        install(new JobsModule());
        install(new ClusterModule());
        install(new SecurityContextModule());
        install(new MetaStatisticsModule());
        install(new SQLStatisticSearchModule());
        install(new ResourceModule());
        install(new JerseyModule());
        install(new PrometheusModule());

        bind(AppInfoProvider.class).to(StroomAppInfoProvider.class);

        HasSystemInfoBinder.create(binder())
                .bind(LogLevelInspector.class);

        // Servlets on the admin path/port
        AdminServletBinder.create(binder())
                .bind(FilteredHealthCheckServlet.class);

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(AdminAccountBootstrapStartup.class, 10);
    }


    // --------------------------------------------------------------------------------


    private static class AdminAccountBootstrapStartup extends RunnableWrapper {

        @Inject
        AdminAccountBootstrapStartup(final AdminAccountBootstrap adminAccountBootstrap) {
            super(adminAccountBootstrap::startup);
        }
    }
}
