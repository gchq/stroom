package stroom.proxy.app;

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
        bind(FailureDestinations.class).to(MockFailureDestinations.class);
    }
}
