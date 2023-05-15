package stroom.util.jersey;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.ws.rs.client.WebTarget;

public class MockJerseyModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JerseyClientFactory.class).to(MockJerseyClientFactory.class);
    }

    @Provides
    WebTarget getWebTarget() {
        return null;
    }
}
