package stroom.event.logging.mock;

import stroom.event.logging.api.StroomEventLoggingService;

import com.google.inject.AbstractModule;

public class MockStroomEventLoggingModule  extends AbstractModule {

    @Override
    protected void configure() {
        bind(StroomEventLoggingService.class).toInstance(new MockStroomEventLoggingService());
    }
}

