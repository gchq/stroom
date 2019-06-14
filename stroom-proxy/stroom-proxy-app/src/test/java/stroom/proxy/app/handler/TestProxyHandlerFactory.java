package stroom.proxy.app.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.proxy.repo.ProxyRepositoryStreamHandlerFactory;
import stroom.proxy.repo.StreamHandler;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;

import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestProxyHandlerFactory extends StroomUnitTest {
    @Mock
    private Client mockJerseyClient;
    @Mock
    private WebTarget mockWebTarget;

    @Test
    void testStoreAndForward() {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(true, true);
        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());

        assertThat(incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler).as("Expecting 1 handler that saves to the repository").isTrue();

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        assertThat(outgoingHandlers.size() == 2 && outgoingHandlers.get(0) instanceof ForwardStreamHandler
                && outgoingHandlers.get(1) instanceof ForwardStreamHandler).as("Expecting 2 handler that forward to other URLS").isTrue();
    }

    @Test
    void testForward() {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(false, true);

        for (int i = 0; i < 2; i++) {
            final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());
            assertThat(incomingHandlers.size() == 2 && incomingHandlers.get(0) instanceof ForwardStreamHandler
                    && incomingHandlers.get(1) instanceof ForwardStreamHandler).as("Expecting 2 handler that forward to other URLS").isTrue();

            assertThat(((ForwardStreamHandler) incomingHandlers.get(0)).getForwardUrl()).isEqualTo("https://url1");
            assertThat(((ForwardStreamHandler) incomingHandlers.get(1)).getForwardUrl()).isEqualTo("https://url2");

            final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
            assertThat(outgoingHandlers.size() == 0).as("Expecting 0 handler that forward to other URLS").isTrue();
        }
    }

    @Test
    void testStore() {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(true, false);

        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());
        assertThat(incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler).as("Expecting 1 handler that stores incoming data").isTrue();

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        assertThat(outgoingHandlers.size() == 0).as("Expecting 1 handlers that forward to other URLS").isTrue();
    }

    private MasterStreamHandlerFactory getProxyHandlerFactory(final boolean isStoringEnabled,
                                                              final boolean isForwardingenabled) {
        final LogStreamConfig logRequestConfig = null;
        final ProxyRepositoryConfig proxyRepositoryConfig = new ProxyRepositoryConfig();
        final ForwardStreamConfig forwardRequestConfig = new ForwardStreamConfig();

        proxyRepositoryConfig.setRepoDir(FileUtil.getCanonicalPath(getCurrentTestDir()));
        proxyRepositoryConfig.setStoringEnabled(isStoringEnabled);

        forwardRequestConfig.setForwardUrl("https://url1,https://url2");
        forwardRequestConfig.setForwardingEnabled(isForwardingenabled);

        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(proxyRepositoryConfig);
        final Provider<ProxyRepositoryStreamHandler> proxyRepositoryRequestHandlerProvider = () -> new ProxyRepositoryStreamHandler(proxyRepositoryManager);

        final LogStream logStream = new LogStream(logRequestConfig);
        final ProxyRepositoryStreamHandlerFactory proxyRepositoryStreamHandlerFactory = new ProxyRepositoryStreamHandlerFactory(proxyRepositoryConfig, proxyRepositoryRequestHandlerProvider);

        // Jersey client only used for dropwiz health checks so not important for tests
        Mockito.when(mockJerseyClient.target(ArgumentMatchers.anyString()))
                .then(url -> mockWebTarget);

        final ForwardStreamHandlerFactory forwardStreamHandlerFactory = new ForwardStreamHandlerFactory(
                logStream, forwardRequestConfig, proxyRepositoryConfig, mockJerseyClient);

        return new MasterStreamHandlerFactory(proxyRepositoryStreamHandlerFactory, forwardStreamHandlerFactory);
    }
}
