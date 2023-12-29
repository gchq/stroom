package stroom.proxy.app.guice;

import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.app.handler.ReceiverFactoryProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class ProxyCoreModule extends AbstractModule {


    @Override
    protected void configure() {
    }

    @SuppressWarnings("unused")
    @Provides
    @Singleton
    ReceiverFactory provideReceiverFactory(final ReceiverFactoryProvider receiverFactoryProvider) {
        return receiverFactoryProvider.get();
    }
}
