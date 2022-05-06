package stroom.proxy.app;

import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.MockForwardDestinations;

import com.google.inject.AbstractModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class TestStoreAndForward extends AbstractTestStoreAndForward {

    @Test
    void testStoreAndForward() throws Exception {
        super.testStoreAndForward();
    }

    @Override
    AbstractModule getModule(final Config configuration, final Path configFile) {
        return new StoreAndForwardTestModule(configuration, configFile);
    }

    @Override
    void await(final ForwarderDestinations forwarderDestinations) {
        final MockForwardDestinations mockForwardDestinations = (MockForwardDestinations) forwarderDestinations;
        while (mockForwardDestinations.awaitNew(0) < 1) {
            // Waiting.
        }
    }
}
