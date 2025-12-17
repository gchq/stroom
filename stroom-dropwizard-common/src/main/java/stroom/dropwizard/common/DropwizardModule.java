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

package stroom.dropwizard.common;

import stroom.util.HasAdminTasks;
import stroom.util.guice.GuiceUtil;
import stroom.util.http.HttpClientFactory;
import stroom.util.metrics.HasMetrics;
import stroom.util.shared.AuthenticationBypassChecker;

import com.codahale.metrics.Metric;
import com.google.inject.AbstractModule;
import io.dropwizard.servlets.tasks.Task;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DropwizardModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AuthenticationBypassChecker.class).to(AuthenticationBypassCheckerImpl.class);
        bind(HttpClientFactory.class).to(DropwizardHttpClientFactory.class);

        // Bind NoMetrics to ensure that the multibinder has at least one binding
        // else it barfs
        GuiceUtil.buildMultiBinder(binder(), HasMetrics.class)
                .addBinding(NoMetrics.class);

        GuiceUtil.buildMultiBinder(binder(), HasAdminTasks.class)
                .addBinding(NoAdminTasks.class);
    }


    // --------------------------------------------------------------------------------


    private static class NoMetrics implements HasMetrics {

        @Override
        public Map<String, Metric> getMetrics() {
            return Collections.emptyMap();
        }
    }


    // --------------------------------------------------------------------------------


    private static class NoAdminTasks implements HasAdminTasks {

        @Override
        public List<Task> getTasks() {
            return Collections.emptyList();
        }
    }
}
