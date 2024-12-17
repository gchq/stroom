package stroom.dropwizard.common;

import stroom.util.http.HttpClientFactory;
import stroom.util.shared.AuthenticationBypassChecker;

import com.google.inject.AbstractModule;

public class DropwizardModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AuthenticationBypassChecker.class).to(AuthenticationBypassCheckerImpl.class);
        bind(HttpClientFactory.class).to(HttpClientFactoryImpl.class);
    }
}
