package stroom.util.jersey;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import jakarta.inject.Singleton;

public class MockJerseyModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JerseyClientFactory.class).to(MockJerseyClientFactory.class);
    }

    @Provides
    @Singleton
    WebTargetFactory provideJerseyRequestBuilder(final JerseyClientFactory jerseyClientFactory) {
        return url ->
                jerseyClientFactory.getDefaultClient().target(url);
    }
}
