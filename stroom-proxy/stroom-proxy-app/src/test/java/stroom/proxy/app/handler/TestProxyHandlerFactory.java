package stroom.proxy.app.handler;

import stroom.proxy.app.forwarder.ForwardDestinationConfig;
import stroom.proxy.app.forwarder.ForwardStreamConfig;
import stroom.proxy.app.forwarder.ForwardStreamHandler;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.proxy.repo.ProxyRepositoryStreamHandlerFactory;
import stroom.proxy.repo.StreamHandler;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.shared.BuildInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Provider;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestProxyHandlerFactory extends StroomUnitTest {

    @Test
    void testStoreAndForward(@TempDir final Path tempDir) {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(tempDir, true, true);
        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());

        assertThat(incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler).as(
                "Expecting 1 handler that saves to the repository").isTrue();

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        assertThat(outgoingHandlers.size() == 2 && outgoingHandlers.get(0) instanceof ForwardStreamHandler
                && outgoingHandlers.get(1) instanceof ForwardStreamHandler).as(
                "Expecting 2 handler that forward to other URLS").isTrue();
    }

    @Test
    void testForward(@TempDir final Path tempDir) {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(tempDir, false, true);

        for (int i = 0; i < 2; i++) {
            final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());
            assertThat(incomingHandlers.size() == 2 && incomingHandlers.get(0) instanceof ForwardStreamHandler
                    && incomingHandlers.get(1) instanceof ForwardStreamHandler).as(
                    "Expecting 2 handler that forward to other URLS").isTrue();

            assertThat(((ForwardStreamHandler) incomingHandlers.get(0)).getForwardUrl()).isEqualTo("https://url1");
            assertThat(((ForwardStreamHandler) incomingHandlers.get(1)).getForwardUrl()).isEqualTo("https://url2");

            final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
            assertThat(outgoingHandlers.size() == 0).as("Expecting 0 handler that forward to other URLS").isTrue();
        }
    }

    @Test
    void testStore(@TempDir final Path tempDir) {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(tempDir, true, false);

        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());
        assertThat(incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler).as(
                "Expecting 1 handler that stores incoming data").isTrue();

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        assertThat(outgoingHandlers.size() == 0).as("Expecting 1 handlers that forward to other URLS").isTrue();
    }

    private MasterStreamHandlerFactory getProxyHandlerFactory(final Path tempDir,
                                                              final boolean isStoringEnabled,
                                                              final boolean isForwardingenabled) {
        final LogStreamConfig logRequestConfig = null;
        final ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
        final ForwardStreamConfig forwardRequestConfig = new ForwardStreamConfig();

        proxyRepoConfig.setRepoDir(FileUtil.getCanonicalPath(getCurrentTestDir()));
        proxyRepoConfig.setStoringEnabled(isStoringEnabled);

        forwardRequestConfig.setForwardingEnabled(isForwardingenabled);
        ForwardDestinationConfig destinationConfig1 = new ForwardDestinationConfig();
        destinationConfig1.setForwardUrl("https://url1");

        ForwardDestinationConfig destinationConfig2 = new ForwardDestinationConfig();
        destinationConfig2.setForwardUrl("https://url2");
        forwardRequestConfig.getForwardDestinations().add(destinationConfig1);
        forwardRequestConfig.getForwardDestinations().add(destinationConfig2);

        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(() -> tempDir,
                proxyRepoConfig);
        final Provider<ProxyRepositoryStreamHandler> proxyRepositoryRequestHandlerProvider = () ->
                new ProxyRepositoryStreamHandler(proxyRepositoryManager);

        final LogStream logStream = new LogStream(logRequestConfig);
        final ProxyRepositoryStreamHandlerFactory proxyRepositoryStreamHandlerFactory =
                new ProxyRepositoryStreamHandlerFactory(proxyRepoConfig, proxyRepositoryRequestHandlerProvider);

        final BuildInfo buildInfo = new BuildInfo("now", "test version", "now");
        final ForwardStreamHandlerFactory forwardStreamHandlerFactory = new ForwardStreamHandlerFactory(
                logStream, forwardRequestConfig, proxyRepoConfig, () -> buildInfo);

        return new MasterStreamHandlerFactory(proxyRepositoryStreamHandlerFactory, forwardStreamHandlerFactory);
    }
}
