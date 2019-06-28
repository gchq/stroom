package stroom.proxy.handler;

import org.junit.Assert;
import org.junit.Test;
import stroom.proxy.repo.ProxyRepositoryConfigImpl;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.proxy.repo.ProxyRepositoryStreamHandlerFactory;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;


public class TestProxyHandlerFactory extends StroomUnitTest {

    @SuppressWarnings("unchecked")
    @Test
    public void testStoreAndForward() throws Exception {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(true, true);
        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());

        Assert.assertTrue("Expecting 1 handler that saves to the repository",
                incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler);

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                outgoingHandlers.size() == 2 && outgoingHandlers.get(0) instanceof ForwardStreamHandler
                        && outgoingHandlers.get(1) instanceof ForwardStreamHandler);
    }

    @Test
    public void testForward() throws Exception {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(false, true);

        for (int i = 0; i < 2; i++) {
            final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    incomingHandlers.size() == 2 && incomingHandlers.get(0) instanceof ForwardStreamHandler
                            && incomingHandlers.get(1) instanceof ForwardStreamHandler);

            Assert.assertEquals("https://url1", ((ForwardStreamHandler) incomingHandlers.get(0)).getForwardUrl());
            Assert.assertEquals("https://url2", ((ForwardStreamHandler) incomingHandlers.get(1)).getForwardUrl());

            final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
            Assert.assertTrue("Expecting 0 handler that forward to other URLS", outgoingHandlers.size() == 0);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStore() throws Exception {
        final MasterStreamHandlerFactory proxyHandlerFactory = getProxyHandlerFactory(true, false);

        final List<StreamHandler> incomingHandlers = proxyHandlerFactory.addReceiveHandlers(new ArrayList<>());
        Assert.assertTrue("Expecting 1 handler that stores incoming data",
                incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryStreamHandler);

        final List<StreamHandler> outgoingHandlers = proxyHandlerFactory.addSendHandlers(new ArrayList<>());
        Assert.assertTrue("Expecting 1 handlers that forward to other URLS", outgoingHandlers.size() == 0);
    }

    private MasterStreamHandlerFactory getProxyHandlerFactory(final boolean isStoringEnabled,
                                                              final boolean isForwardingenabled) {
        final LogStreamConfig logRequestConfig = null;
        final ProxyRepositoryConfigImpl proxyRepositoryConfig = new ProxyRepositoryConfigImpl();
        final ForwardStreamConfig forwardRequestConfig = new ForwardStreamConfig();

        proxyRepositoryConfig.setRepoDir(FileUtil.getCanonicalPath(getCurrentTestDir()));
        proxyRepositoryConfig.setStoringEnabled(isStoringEnabled);

        forwardRequestConfig.setForwardingEnabled(isForwardingenabled);
        ForwardDestinationConfig destinationConfig1 = new ForwardDestinationConfig();
        destinationConfig1.setForwardUrl("https://url1");

        ForwardDestinationConfig destinationConfig2 = new ForwardDestinationConfig();
        destinationConfig2.setForwardUrl("https://url2");
        forwardRequestConfig.getForwardDestinations().add(destinationConfig1);
        forwardRequestConfig.getForwardDestinations().add(destinationConfig2);

        final ProxyRepositoryManager proxyRepositoryManager = new ProxyRepositoryManager(proxyRepositoryConfig);
        final Provider<ProxyRepositoryStreamHandler> proxyRepositoryRequestHandlerProvider = () ->
                new ProxyRepositoryStreamHandler(proxyRepositoryManager);

        final LogStream logStream = new LogStream(logRequestConfig);
        final ProxyRepositoryStreamHandlerFactory proxyRepositoryStreamHandlerFactory =
                new ProxyRepositoryStreamHandlerFactory(proxyRepositoryConfig, proxyRepositoryRequestHandlerProvider);

        final ForwardStreamHandlerFactory forwardStreamHandlerFactory = new ForwardStreamHandlerFactory(
                logStream, forwardRequestConfig, proxyRepositoryConfig);

        return new MasterStreamHandlerFactory(proxyRepositoryStreamHandlerFactory, forwardStreamHandlerFactory);
    }
}
