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

package stroom.config.global.impl;

import stroom.config.global.api.GlobalConfig;
import stroom.job.api.ScheduledJobsBinder;
import stroom.ui.config.shared.UserPreferencesService;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;

public class GlobalConfigModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(AppConfigMonitor.class).asEagerSingleton();
        bind(GlobalConfig.class).to(GlobalConfigService.class);

        HasHealthCheckBinder.create(binder())
                .bind(AppConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(AppConfigMonitor.class);

        bind(UserPreferencesService.class).to(UserPreferencesServiceImpl.class);
        RestResourcesBinder.create(binder())
                .bind(GlobalConfigResourceImpl.class);
        RestResourcesBinder.create(binder())
                .bind(UserPreferencesResourceImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(AppConfigSystemInfo.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PropertyCacheReload.class, builder -> builder
                        .name("Property Cache Reload")
                        .description("Reload properties in the cluster")
                        .frequencySchedule("1m"));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    private static class PropertyCacheReload extends RunnableWrapper {

        @Inject
        PropertyCacheReload(final GlobalConfigService globalConfigService) {
            super(globalConfigService::updateConfigObjects);
        }
    }
}
