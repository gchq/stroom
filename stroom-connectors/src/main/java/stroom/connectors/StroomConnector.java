package stroom.connectors;

import com.codahale.metrics.health.HealthCheck;

public interface StroomConnector {

    /**
     * Retrieve the health for a given connector.
     * @return The health status for this connector.
     */
    HealthCheck.Result getHealth();

    /**
     * Should be called when the producer is no longer required.
     */
    void shutdown();
}
