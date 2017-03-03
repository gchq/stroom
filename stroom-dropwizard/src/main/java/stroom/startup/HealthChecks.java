package stroom.startup;

import com.codahale.metrics.health.HealthCheck;

public class HealthChecks {
    public HealthChecks(io.dropwizard.setup.Environment environment, ApiResources apiResources){
        environment.healthChecks().register("SearchResourceHealthCheck", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return apiResources.searchResource.getHealth();
            }
        });
    }
}
