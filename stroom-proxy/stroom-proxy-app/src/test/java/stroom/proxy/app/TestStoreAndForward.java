package stroom.proxy.app;

import stroom.proxy.app.guice.ProxyCoreModule;

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
        return new ProxyCoreModule();
    }
}
