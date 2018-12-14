package stroom.proxy.handler;


import org.junit.jupiter.api.Test;
import stroom.proxy.repo.ProxyRepositoryConfigImpl;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.proxy.repo.ProxyRepositoryStreamHandlerFactory;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestProxyHandlerFactory extends StroomUnitTest {
    @SuppressWarnings("unchecked")
    @Test
    void testStoreAndForward() throws Exception {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(true, true);
        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());

        assertThat(incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler).as("Expecting 1 handler that saves to the repository").isTrue();

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        assertThat(outgoingHandlers.size() == 2 && outgoingHandlers.get(0) instanceof ForwardStreamHandler
                && outgoingHandlers.get(1) instanceof ForwardStreamHandler).as("Expecting 2 handler that forward to other URLS").isTrue();
    }

    @Test
    void testForward() throws Exception {
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

    @SuppressWarnings("unchecked")
    @Test
    void testStore() throws Exception {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(true, false);

        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());
        assertThat(incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler).as("Expecting 1 handler that stores incoming data").isTrue();

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        assertThat(outgoingHandlers.size() == 0).as("Expecting 1 handlers that forward to other URLS").isTrue();
    }

    private MasterStreamHandlerFactory getProxyHandlerFactory(final boolean isStoringEnabled,
                                                              final boolean isForwardingenabled) {
        final LogStreamConfig logRequestConfig = null;
        final ProxyRepositoryConfigImpl proxyRepositoryConfig = new ProxyRepositoryConfigImpl();
        final ForwardStreamConfig forwardRequestConfig = new ForwardStreamConfig();

        proxyRepositoryConfig.setRepoDir(FileUtil.getCanonicalPath(getCurrentTestDir()));
        proxyRepositoryConfig.setStoringEnabled(isStoringEnabled);

        forwardRequestConfig.setForwardUrl("https://url1,https://url2");
        forwardRequestConfig.setForwardingEnabled(isForwardingenabled);

        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(proxyRepositoryConfig);
        final Provider<ProxyRepositoryStreamHandler> proxyRepositoryRequestHandlerProvider = () -> new ProxyRepositoryStreamHandler(proxyRepositoryManager);

        final LogStream logStream = new LogStream(logRequestConfig);
        final ProxyRepositoryStreamHandlerFactory proxyRepositoryStreamHandlerFactory = new ProxyRepositoryStreamHandlerFactory(proxyRepositoryConfig, proxyRepositoryRequestHandlerProvider);
        final ForwardStreamHandlerFactory forwardStreamHandlerFactory = new ForwardStreamHandlerFactory(logStream, forwardRequestConfig, proxyRepositoryConfig);

        return new MasterStreamHandlerFactory(proxyRepositoryStreamHandlerFactory, forwardStreamHandlerFactory);
    }
}
