package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwardFileHandlers;
import stroom.proxy.app.forwarder.ForwarderDestinationsImpl;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.store.SequentialFileStore;

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
        final ForwardFileHandlers handlers = (ForwardFileHandlers) destinations.getProvider("test");
        final SequentialFileStore sequentialFileStore = handlers.getSequentialFileStore();
        while (sequentialFileStore.awaitNew(0) < 1) {
            // Waiting.
        }
    }
}
