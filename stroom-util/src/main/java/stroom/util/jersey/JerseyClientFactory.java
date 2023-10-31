package stroom.util.jersey;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;

public interface JerseyClientFactory {

    /**
     * @return The client associated with the configuration for jerseyClientName or if there is
     * no configuration for that name, it will provide the client for {@link JerseyClientName#DEFAULT}.
     */
    Client getNamedClient(final JerseyClientName jerseyClientName);

    /**
     * @return The client associated with the configuration for {@link JerseyClientName#DEFAULT}.
     */
    Client getDefaultClient();

    /**
     * Helper method to create a {@link WebTarget} using the named client.
     */
    WebTarget createWebTarget(final JerseyClientName jerseyClientName,
                              final String endpoint);
}
