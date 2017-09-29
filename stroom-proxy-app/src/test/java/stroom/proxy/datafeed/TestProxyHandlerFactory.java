package stroom.proxy.datafeed;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.proxy.guice.ProxyStoreModule;
import stroom.proxy.handler.ForwardRequestHandler;
import stroom.proxy.handler.ProxyRepositoryRequestHandler;
import stroom.proxy.handler.RequestHandler;
import stroom.proxy.util.ProxyProperties;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;

import java.util.List;
import java.util.Properties;

@Ignore
public class TestProxyHandlerFactory extends StroomUnitTest {
    private ProxyHandlerFactory getProxyHandlerFactory() {
        final Injector injector = Guice.createInjector(new ProxyStoreModule());
        return injector.getInstance(ProxyHandlerFactory.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStoreAndForward() throws Exception {
        try {
            final Properties properties = new Properties();
            properties.setProperty(ProxyProperties.FORWARD_URL, "https://url1,https://url2");
            properties.setProperty(ProxyProperties.REPO_DIR, FileUtil.getCanonicalPath(getCurrentTestDir()));
            ProxyProperties.setOverrideProperties(properties);

            final ProxyHandlerFactory proxyHandlerFactory = getProxyHandlerFactory();
            final List<RequestHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();

            Assert.assertTrue("Expecting 1 handler that saves to the repository",
                    incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryRequestHandler);

            final List<RequestHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    outgoingHandlers.size() == 2 && outgoingHandlers.get(0) instanceof ForwardRequestHandler
                            && outgoingHandlers.get(1) instanceof ForwardRequestHandler);

        } finally {
            ProxyProperties.setOverrideProperties(null);
        }
    }

    @Test
    public void testForward() throws Exception {
        try {
            final Properties properties = new Properties();
            properties.setProperty(ProxyProperties.FORWARD_URL, "https://url1,https://url2");

            ProxyProperties.setOverrideProperties(properties);

            final ProxyHandlerFactory proxyHandlerFactory = getProxyHandlerFactory();

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

        } finally {
            ProxyProperties.setOverrideProperties(null);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStore() throws Exception {
        try {
            final Properties properties = new Properties();
            properties.setProperty(ProxyProperties.REPO_DIR, FileUtil.getCanonicalPath(getCurrentTestDir()));

            ProxyProperties.setOverrideProperties(properties);

            final ProxyHandlerFactory proxyHandlerFactory = getProxyHandlerFactory();

            final List<RequestHandler> incomingHandlers = proxyHandlerFactory.createIncomingHandlers();
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    incomingHandlers.size() == 1 && incomingHandlers.get(0) instanceof ProxyRepositoryRequestHandler);

            final List<RequestHandler> outgoingHandlers = proxyHandlerFactory.createOutgoingHandlers();
            Assert.assertTrue("Expecting 0 handler that forward to other URLS", outgoingHandlers.size() == 0);
        } finally {
            ProxyProperties.setOverrideProperties(null);
        }
    }
}
