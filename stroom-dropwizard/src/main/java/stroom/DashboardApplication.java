package stroom;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import stroom.health.TemplateHealthCheck;
import stroom.resources.HelloWorldResource;

public class DashboardApplication extends Application<DashboardConfiguration> {

    public static void main(final String[] args) throws Exception {
        new DashboardApplication().run(args);
    }

    @Override
    public String getName() {
        return "Dashboard";
    }

    @Override
    public void initialize(final Bootstrap<DashboardConfiguration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/gwt", "/", "index.html", "gwt"));
    }

    @Override
    public void run(final DashboardConfiguration configuration,
                    final Environment environment) {
        final HelloWorldResource resource = new HelloWorldResource(
                configuration.getTemplate(),
                configuration.getDefaultName()
        );
        final TemplateHealthCheck healthCheck =
                new TemplateHealthCheck(configuration.getTemplate());
        environment.healthChecks().register("template", healthCheck);
        environment.jersey().register(resource);

        environment.jersey().setUrlPattern("/api/*");
    }
}
