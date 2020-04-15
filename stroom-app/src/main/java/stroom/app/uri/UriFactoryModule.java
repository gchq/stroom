package stroom.app.uri;

import com.google.inject.AbstractModule;
import stroom.config.common.UriFactory;

public class UriFactoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(UriFactory.class).to(UriFactoryImpl.class);
    }
}
