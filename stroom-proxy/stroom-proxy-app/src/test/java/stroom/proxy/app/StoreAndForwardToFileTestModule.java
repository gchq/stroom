package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderDestinationsImpl;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.Sender;
import stroom.proxy.repo.SenderImpl;

import java.nio.file.Path;

public class StoreAndForwardToFileTestModule extends AbstractStoreAndForwardTestModule {

    public StoreAndForwardToFileTestModule(final Config configuration,
                                           final Path configFile) {
        super(configuration, configFile);
    }

    @Override
    protected void configure() {
        super.configure();
        bind(Sender.class).to(SenderImpl.class);
        bind(ForwarderDestinations.class).to(ForwarderDestinationsImpl.class);
    }
}
