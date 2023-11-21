package stroom.util.jersey;

import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

@Singleton
public class MockJerseyClientFactory implements JerseyClientFactory {

    private final Client client = ClientBuilder.newClient();

    @Override
    public Client getNamedClient(final JerseyClientName jerseyClientName) {
        return client;
    }

    @Override
    public Client getDefaultClient() {
        return client;
    }

    @Override
    public WebTarget createWebTarget(final JerseyClientName jerseyClientName, final String endpoint) {
        return client.target(endpoint);
    }
}
