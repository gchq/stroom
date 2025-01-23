package stroom.dropwizard.common;

import stroom.util.HasAdminTasks;
import stroom.util.HasMetrics;
import stroom.util.guice.GuiceUtil;
import stroom.util.http.HttpClientFactory;
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
