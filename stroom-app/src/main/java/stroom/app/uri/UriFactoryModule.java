package stroom.app.uri;

import stroom.config.common.UriFactory;

import com.google.inject.AbstractModule;

public class UriFactoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(UriFactory.class).to(UriFactoryImpl.class);
    }
}
