package stroom.security.mock;

import stroom.security.api.ServiceUserFactory;

import com.google.inject.AbstractModule;

public class MockServiceUserFactoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ServiceUserFactory.class).to(MockServiceUserFactory.class);
    }
}
