package stroom.proxy.app.guice;

import stroom.proxy.app.Config;
import stroom.proxy.app.ProxyConfig;
import stroom.test.common.util.guice.GuiceTestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.Module;
import io.dropwizard.setup.Environment;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;

class TestProxyModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyModule.class);

    @Test
    void dumpGuiceModulesTree() {
        final Module proxyModule = getModule();
        final String dump = GuiceTestUtil.dumpGuiceModuleHierarchy(proxyModule);
        LOGGER.info("\n{}", dump);
    }

    @Test
    void dumpGuiceBindsSortedByKey() {
        final Module proxyModule = getModule();
        final String dump = GuiceTestUtil.dumpBindsSortedByKey(proxyModule);
        LOGGER.info("\n{}", dump);
    }

    @NotNull
    private Module getModule() {
        final Environment environmentMock = Mockito.mock(Environment.class);
        Mockito.when(environmentMock.healthChecks())
                .thenReturn(new HealthCheckRegistry());

        final Config config = new Config();
        config.setProxyConfig(new ProxyConfig());

        return new ProxyModule(config, environmentMock, Path.of("DUMMY"));
    }

}
