package stroom.dropwizard.common.prometheus;

import stroom.util.guice.AdminServletBinder;

import com.google.inject.AbstractModule;

public class PrometheusModule extends AbstractModule {

    @Override
    protected void configure() {
        AdminServletBinder.create(binder())
                .bind(PrometheusMetricsServlet.class);
    }
}
