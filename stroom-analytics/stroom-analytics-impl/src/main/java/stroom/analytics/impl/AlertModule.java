/*
 * Copyright 2020 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.api.AlertManager;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class AlertModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AlertManager.class).to(AlertManagerImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(AlertManagerCacheRefresh.class, builder -> builder
                        .name("Alert Rule Refresh")
                        .description("Update alert generation rules with recent changes")
                        .schedule(PERIODIC, "10m")
                        .advanced(true)
                        .enabled(false));
    }

    private static class AlertManagerCacheRefresh extends RunnableWrapper {

        @Inject
        AlertManagerCacheRefresh(final AlertManager alertManager) {
            super(alertManager::refreshRules);
        }
    }
}

