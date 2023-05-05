package stroom.util.jersey;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

public class MockJerseyClientFactory implements JerseyClientFactory {

    @Override
    public Client getNamedClient(final JerseyClientName jerseyClientName) {
        return null;
    }

    @Override
    public Client getDefaultClient() {
        return null;
    }

    @Override
    public WebTarget createWebTarget(final JerseyClientName jerseyClientName, final String endpoint) {
        return null;
    }
}
