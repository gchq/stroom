package stroom.proxy.app;

import stroom.proxy.app.handler.ForwardFileDestination;
import stroom.proxy.app.forwarder.ForwarderDestinationsImpl;

import com.google.inject.AbstractModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class TestStoreAndForwardToFile extends AbstractTestStoreAndForward {

    @Test
    void testStoreAndForward() throws Exception {
        super.testStoreAndForward();
    }

    @Override
    AbstractModule getModule(final Config configuration, final Path configFile) {
        return new StoreAndForwardToFileTestModule(configuration, configFile);
    }

    @Override
    void await(final ForwarderDestinations forwarderDestinations) {
        final ForwarderDestinationsImpl destinations = (ForwarderDestinationsImpl) forwarderDestinations;
        final ForwardFileDestination handlers = (ForwardFileDestination) destinations.getProvider("test");
        final SequentialFileStore sequentialFileStore = handlers.getSequentialFileStore();
        while (sequentialFileStore.awaitNew(0) < 1) {
            // Waiting.
        }
    }
}
