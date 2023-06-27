package stroom.dropwizard.common;

import stroom.util.shared.ServletAuthenticationChecker;

import com.google.inject.AbstractModule;


public class DropwizardModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ServletAuthenticationChecker.class).to(Servlets.class);
    }
}
