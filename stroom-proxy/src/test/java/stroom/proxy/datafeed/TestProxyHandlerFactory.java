package stroom.proxy.datafeed;

import org.junit.Assert;
import org.junit.Test;
import stroom.proxy.handler.ForwardStreamConfig;
import stroom.proxy.handler.ForwardStreamHandler;
import stroom.proxy.handler.ForwardStreamHandlerFactory;
import stroom.proxy.handler.LogStreamConfig;
import stroom.proxy.handler.LogStreamHandler;
import stroom.proxy.handler.StreamHandler;
import stroom.proxy.handler.StreamHandlerFactoryImpl;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import javax.inject.Provider;
import java.util.List;

public class TestProxyHandlerFactory extends StroomUnitTest {
    @SuppressWarnings("unchecked")
    @Test
    public void testStoreAndForward() throws Exception {
        final StreamHandlerFactoryImpl proxyHandlerFactory = getProxyHandlerFactory(FileUtil.getCanonicalPath(getCurrentTestDir()),
                "https://url1,https://url2");
        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();

        Assert.assertTrue("Expecting 1 handler that saves to the repository",
                incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler);

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
        Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                outgoingHandlers.size() == 2 && outgoingHandlers.get(0) instanceof ForwardStreamHandler
                        && outgoingHandlers.get(1) instanceof ForwardStreamHandler);
    }

    @Test
    public void testForward() throws Exception {
        final StreamHandlerFactoryImpl proxyHandlerFactory = getProxyHandlerFactory(null, "https://url1,https://url2");

        for (int i = 0; i < 2; i++) {
            final List<StreamHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    incomingHandlers.size() == 2 && incomingHandlers.get(0) instanceof ForwardStreamHandler
                            && incomingHandlers.get(1) instanceof ForwardStreamHandler);

            Assert.assertEquals("https://url1", ((ForwardStreamHandler) incomingHandlers.get(0)).getForwardUrl());
            Assert.assertEquals("https://url2", ((ForwardStreamHandler) incomingHandlers.get(1)).getForwardUrl());

            final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
            Assert.assertTrue("Expecting 0 handler that forward to other URLS", outgoingHandlers.size() == 0);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStore() throws Exception {
        final StreamHandlerFactoryImpl proxyHandlerFactory = getProxyHandlerFactory(FileUtil.getCanonicalPath(getCurrentTestDir()), null);

        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();
        Assert.assertTrue("Expecting 1 handler that stores incoming data",
                incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler);

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
        Assert.assertTrue("Expecting 0 handlers that forward to other URLS", outgoingHandlers.size() == 0);
    }

    private StreamHandlerFactoryImpl getProxyHandlerFactory(final String repoDir, final String forwardUrl) {
        final LogStreamConfig logRequestConfig = new LogStreamConfig();
        final ProxyRepositoryConfig proxyRepositoryConfig = new ProxyRepositoryConfig();
        final ForwardStreamConfig forwardRequestConfig = new ForwardStreamConfig();

        logRequestConfig.setLogRequest("");
        proxyRepositoryConfig.setRepoDir(repoDir);
        forwardRequestConfig.setForwardUrl(forwardUrl);

        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(proxyRepositoryConfig);

        final Provider<ProxyRepositoryStreamHandler> proxyRepositoryRequestHandlerProvider = () -> new ProxyRepositoryStreamHandler(proxyRepositoryManager);

        final Provider<LogStreamHandler> logRequestHandlerProvider = () -> new LogStreamHandler(logRequestConfig);

        final ForwardStreamHandlerFactory forwardRequestHandlerFactory = new ForwardStreamHandlerFactory(forwardRequestConfig);

        return new StreamHandlerFactoryImpl(logRequestConfig,
                proxyRepositoryConfig,
                proxyRepositoryRequestHandlerProvider,
                logRequestHandlerProvider,
                forwardRequestHandlerFactory);
    }
}
