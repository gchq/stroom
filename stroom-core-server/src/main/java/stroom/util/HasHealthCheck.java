package stroom.util;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface HasHealthCheck {

    Logger LOGGER = LoggerFactory.getLogger(HasHealthCheck.class);

    HealthCheck.Result getHealth();

    default HealthCheck getHealthCheck() {
        return new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return getHealth();
            }
        };
    }

}