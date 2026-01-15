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

import stroom.proxy.app.ProxyConfigHolder;
import stroom.proxy.app.ProxyConfigMonitor;
import stroom.util.config.ConfigLocation;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.io.DirProvidersModule;
import stroom.util.validation.ValidationModule;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;

public class ProxyConfigModule extends AbstractModule {

    private final ProxyConfigHolder proxyConfigHolder;

    public ProxyConfigModule(final ProxyConfigHolder proxyConfigHolder) {
        this.proxyConfigHolder = proxyConfigHolder;
    }

    @Override
    protected void configure() {
        bind(ProxyConfigHolder.class).toInstance(proxyConfigHolder);

        bind(ProxyConfigMonitor.class).asEagerSingleton();

        install(new ProxyConfigProvidersModule());
        install(new DirProvidersModule());
        install(new ValidationModule());

        HasHealthCheckBinder.create(binder())
                .bind(ProxyConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(ProxyConfigMonitor.class);

        HasHealthCheckBinder.create(binder())
                .bind(ProxyConfigMonitor.class);

        // Holder for the location of the yaml config file so the AppConfigMonitor can
        // get hold of it via guice
        bind(ConfigLocation.class)
                .toInstance(new ConfigLocation(proxyConfigHolder.getConfigFile()));
    }
}
