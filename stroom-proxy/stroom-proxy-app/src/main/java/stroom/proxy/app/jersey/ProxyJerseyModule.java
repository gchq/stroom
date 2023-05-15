package stroom.proxy.app.jersey;

import stroom.util.jersey.JerseyClientFactory;

import com.google.inject.AbstractModule;

public class ProxyJerseyModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(JerseyClientFactory.class).to(ProxyJerseyClientFactoryImpl.class);
    }
}
