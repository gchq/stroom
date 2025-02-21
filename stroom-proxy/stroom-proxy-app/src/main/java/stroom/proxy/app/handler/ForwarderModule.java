package stroom.proxy.app.handler;

import com.google.inject.AbstractModule;

public class ForwarderModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ForwardFileDestinationFactory.class).to(ForwardFileDestinationFactoryImpl.class);
        bind(ForwardHttpPostDestinationFactory.class).to(ForwardHttpPostDestinationFactoryImpl.class);
    }
}
