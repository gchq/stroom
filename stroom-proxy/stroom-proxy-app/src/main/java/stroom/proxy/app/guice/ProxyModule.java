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

package stroom.proxy.app.guice;

import stroom.dropwizard.common.DropwizardModule;
import stroom.dropwizard.common.FilteredHealthCheckServlet;
import stroom.dropwizard.common.LogLevelInspector;
import stroom.dropwizard.common.PermissionExceptionMapper;
import stroom.dropwizard.common.TokenExceptionMapper;
import stroom.dropwizard.common.prometheus.AppInfoProvider;
import stroom.dropwizard.common.prometheus.PrometheusModule;
import stroom.proxy.app.Config;
import stroom.proxy.app.ProxyConfigHealthCheck;
import stroom.proxy.app.ProxyConfigHolder;
import stroom.proxy.app.ProxyLifecycle;
import stroom.proxy.app.ReceiveDataRuleSetClient;
import stroom.proxy.app.event.EventResourceImpl;
import stroom.proxy.app.handler.ForwarderModule;
import stroom.proxy.app.handler.RemoteFeedStatusClient;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.metrics.ProxyAppInfoProvider;
import stroom.proxy.app.security.ProxyApiKeyCheckClient;
import stroom.proxy.app.servlet.ProxyQueueMonitoringServlet;
import stroom.proxy.app.servlet.ProxySecurityFilter;
import stroom.proxy.app.servlet.ProxyStatusServlet;
import stroom.proxy.app.servlet.ProxyWelcomeServlet;
import stroom.receive.common.DataFeedKeyDirWatcher;
import stroom.receive.common.DebugServlet;
import stroom.receive.common.FeedStatusResourceImpl;
import stroom.receive.common.FeedStatusResourceV2Impl;
import stroom.receive.common.ReceiveDataRuleSetResourceImpl;
import stroom.receive.common.ReceiveDataServlet;
import stroom.security.common.impl.RefreshManager;
import stroom.util.guice.AdminServletBinder;
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;
import stroom.util.metrics.Metrics;
import stroom.util.metrics.MetricsImpl;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.nio.file.Path;

public class ProxyModule extends AbstractModule {

    private static final String MATCH_ALL_PATHS = "/*";

    private final Config configuration;
    private final Environment environment;
    private final ProxyConfigHolder proxyConfigHolder;

    public ProxyModule(final Config configuration,
                       final Environment environment,
                       final Path configFile) {
        this.configuration = configuration;
        this.environment = environment;

        proxyConfigHolder = new ProxyConfigHolder(
                configuration.getProxyConfig(),
                configFile);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);
        bind(MetricRegistry.class).toInstance(environment.metrics());
        bind(HealthCheckRegistry.class).toInstance(environment.healthChecks());
        bind(Metrics.class).to(MetricsImpl.class);
        bind(AppInfoProvider.class).to(ProxyAppInfoProvider.class);

        install(new ProxyConfigModule(proxyConfigHolder));
        install(new ProxyCoreModule());
        install(new DropwizardModule());
        install(new ForwarderModule());
        install(new PrometheusModule());

        HasHealthCheckBinder.create(binder())
                .bind(DataFeedKeyDirWatcher.class)
                .bind(LogLevelInspector.class)
                .bind(ProxyConfigHealthCheck.class)
                .bind(ProxyApiKeyCheckClient.class)
                .bind(ReceiveDataRuleSetClient.class)
                .bind(RemoteFeedStatusClient.class);

        FilterBinder.create(binder())
                .bind(new FilterInfo(ProxySecurityFilter.class.getSimpleName(), MATCH_ALL_PATHS),
                        ProxySecurityFilter.class);

        ServletBinder.create(binder())
                .bind(DebugServlet.class)
                .bind(ProxyStatusServlet.class)
                .bind(ProxyQueueMonitoringServlet.class)
                .bind(ProxyWelcomeServlet.class)
                .bind(ReceiveDataServlet.class);

        AdminServletBinder.create(binder())
                .bind(FilteredHealthCheckServlet.class);

        RestResourcesBinder.create(binder())
                .bind(ReceiveDataRuleSetResourceImpl.class)
                .bind(FeedStatusResourceImpl.class)
                .bind(FeedStatusResourceV2Impl.class)
                .bind(EventResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(ProxyLifecycle.class)
                .addBinding(RemoteFeedStatusService.class)
                .addBinding(RefreshManager.class);

        GuiceUtil.buildMultiBinder(binder(), ExceptionMapper.class)
                .addBinding(PermissionExceptionMapper.class)
                .addBinding(TokenExceptionMapper.class);
    }
}
