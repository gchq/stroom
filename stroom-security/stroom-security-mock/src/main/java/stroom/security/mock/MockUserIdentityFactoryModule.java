package stroom.security.mock;

import stroom.security.api.UserIdentityFactory;

import com.google.inject.AbstractModule;

public class MockUserIdentityFactoryModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new MockServiceUserFactoryModule());
        bind(UserIdentityFactory.class).to(MockUserIdentityFactory.class);
    }
}
