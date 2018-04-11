package stroom.util;

import com.codahale.metrics.health.HealthCheck;

public interface HasHealthCheck {
    HealthCheck.Result getHealth();

    default HealthCheck getHealthCheck() {
        return new HealthCheck() {
            @Override
            protected Result check() {
                return getHealth();
            }
        };
    }
}