package stroom.proxy.app;

import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.MockForwardDestinations;
import stroom.proxy.repo.MockSender;
import stroom.proxy.repo.Sender;

import java.nio.file.Path;

public class StoreAndForwardTestModule extends AbstractStoreAndForwardTestModule {

    public StoreAndForwardTestModule(final Config configuration,
                                     final Path configFile) {
        super(configuration, configFile);
    }

    @Override
    protected void configure() {
        super.configure();
        bind(Sender.class).to(MockSender.class);
        bind(ForwarderDestinations.class).to(MockForwardDestinations.class);
    }
}
