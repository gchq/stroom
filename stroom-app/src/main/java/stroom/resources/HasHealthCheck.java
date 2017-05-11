package stroom.resources;

import com.codahale.metrics.health.HealthCheck;

public interface HasHealthCheck {

    HealthCheck.Result getHealth();

}
