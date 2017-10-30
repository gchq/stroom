package stroom.proxy.datafeed;

import org.junit.Assert;
import org.junit.Test;
import stroom.proxy.handler.ForwardRequestConfig;
import stroom.proxy.handler.ForwardRequestHandler;
import stroom.proxy.handler.ForwardRequestHandlerFactory;
import stroom.proxy.handler.LogRequestConfig;
import stroom.proxy.handler.LogRequestHandler;
import stroom.proxy.handler.RequestHandler;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryRequestHandler;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import javax.inject.Provider;
import java.util.List;

public class TestProxyHandlerFactory extends StroomUnitTest {
    @SuppressWarnings("unchecked")
    @Test
    public void testStoreAndForward() throws Exception {
        final ProxyHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(FileUtil.getCanonicalPath(getCurrentTestDir()),
                "https://url1,https://url2");
        final List<RequestHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();

        Assert.assertTrue("Expecting 1 handler that saves to the repository",
                incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryRequestHandler);

        final List<RequestHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
        Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                outgoingHandlers.size() == 2 && outgoingHandlers.get(0) instanceof ForwardRequestHandler
                        && outgoingHandlers.get(1) instanceof ForwardRequestHandler);
    }

    @Test
    public void testForward() throws Exception {
        final ProxyHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(null, "https://url1,https://url2");

        for (int i = 0; i < 2; i++) {
            final List<RequestHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    incomingHandlers.size() == 2 && incomingHandlers.get(0) instanceof ForwardRequestHandler
                            && incomingHandlers.get(1) instanceof ForwardRequestHandler);

            Assert.assertEquals("https://url1", ((ForwardRequestHandler) incomingHandlers.get(0)).getForwardUrl());
            Assert.assertEquals("https://url2", ((ForwardRequestHandler) incomingHandlers.get(1)).getForwardUrl());

            final List<RequestHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
            Assert.assertTrue("Expecting 0 handler that forward to other URLS", outgoingHandlers.size() == 0);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStore() throws Exception {
        final ProxyHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(FileUtil.getCanonicalPath(getCurrentTestDir()), null);

        final List<RequestHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();
        Assert.assertTrue("Expecting 1 handler that stores incoming data",
                incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryRequestHandler);

        final List<RequestHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
        Assert.assertTrue("Expecting 0 handlers that forward to other URLS", outgoingHandlers.size() == 0);
    }

    private ProxyHandlerFactory getProxyHandlerFactory(final String repoDir, final String forwardUrl) {
        final LogRequestConfig logRequestConfig = new LogRequestConfig();
        final ProxyRepositoryConfig proxyRepositoryConfig = new ProxyRepositoryConfig();
        final ForwardRequestConfig forwardRequestConfig = new ForwardRequestConfig();

        logRequestConfig.setLogRequest("");
        proxyRepositoryConfig.setRepoDir(repoDir);
        forwardRequestConfig.setForwardUrl(forwardUrl);

        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(proxyRepositoryConfig);

        final Provider<ProxyRepositoryRequestHandler> proxyRepositoryRequestHandlerProvider = () -> new ProxyRepositoryRequestHandler(proxyRepositoryManager);

        final Provider<LogRequestHandler> logRequestHandlerProvider = () -> new LogRequestHandler(logRequestConfig);

        final ForwardRequestHandlerFactory forwardRequestHandlerFactory = new ForwardRequestHandlerFactory(forwardRequestConfig);

        return new ProxyHandlerFactory(logRequestConfig,
                proxyRepositoryConfig,
                proxyRepositoryRequestHandlerProvider,
                logRequestHandlerProvider,
                forwardRequestHandlerFactory);
    }
}
