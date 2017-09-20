package stroom.proxy.datafeed;

import org.junit.Assert;
import org.junit.Test;
import stroom.proxy.handler.ForwardRequestHandler;
import stroom.proxy.handler.ProxyRepositoryRequestHandler;
import stroom.proxy.handler.RequestHandler;
import stroom.proxy.util.ProxyProperties;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;
import stroom.util.thread.ThreadScopeContextHolder;

import java.util.List;
import java.util.Properties;

public class TestProxyHandlerFactory extends StroomUnitTest {
    @SuppressWarnings("unchecked")
    @Test
    public void testStoreAndForward() throws Exception {
        try {
            final Properties properties = new Properties();
            properties.setProperty(ProxyProperties.FORWARD_URL, "https://url1,https://url2");
            properties.setProperty(ProxyProperties.REPO_DIR, FileUtil.getCanonicalPath(getCurrentTestDir()));
            ProxyProperties.setOverrideProperties(properties);

            final ProxyHandlerFactory proxyHandlerFactory = new ProxyHandlerFactory();
            Assert.assertEquals(ProxyHandlerFactory.STORE_AND_FORWARD_HANDLER_CONTEXT,
                    proxyHandlerFactory.getContextFile());

            List<RequestHandler> handlers = proxyHandlerFactory.getIncomingRequestHandlerList();

            Assert.assertTrue("Expecting 1 handler that saves to the repository",
                    handlers.size() == 1 && handlers.get(0) instanceof ProxyRepositoryRequestHandler);

            handlers = (List<RequestHandler>) proxyHandlerFactory.getContext().getBean("outgoingRequestHandlerList");
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    handlers.size() == 2 && handlers.get(0) instanceof ForwardRequestHandler
                            && handlers.get(1) instanceof ForwardRequestHandler);

        } finally {
            ProxyProperties.setOverrideProperties(null);
        }
    }

    @Test
    public void testForward() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty(ProxyProperties.FORWARD_URL, "https://url1,https://url2");

        ProxyProperties.setOverrideProperties(properties);

        final ProxyHandlerFactory proxyHandlerFactory = new ProxyHandlerFactory();

        try {
            Assert.assertEquals(ProxyHandlerFactory.FORWARD_HANDLER_CONTEXT, proxyHandlerFactory.getContextFile());

            List<RequestHandler> handlers = null;

            handlers = proxyHandlerFactory.getIncomingRequestHandlerList();
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    handlers.size() == 2 && handlers.get(0) instanceof ForwardRequestHandler
                            && handlers.get(1) instanceof ForwardRequestHandler);

            Assert.assertEquals("https://url1", ((ForwardRequestHandler) handlers.get(0)).getForwardUrl());
            Assert.assertEquals("https://url2", ((ForwardRequestHandler) handlers.get(1)).getForwardUrl());

        } finally {
            ThreadScopeContextHolder.destroyContext();
        }

        try {
            ThreadScopeContextHolder.createContext();

            List<RequestHandler> handlers = null;

            handlers = proxyHandlerFactory.getIncomingRequestHandlerList();
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    handlers.size() == 2 && handlers.get(0) instanceof ForwardRequestHandler
                            && handlers.get(1) instanceof ForwardRequestHandler);

            Assert.assertEquals("https://url1", ((ForwardRequestHandler) handlers.get(0)).getForwardUrl());
            Assert.assertEquals("https://url2", ((ForwardRequestHandler) handlers.get(1)).getForwardUrl());

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

            final ProxyHandlerFactory proxyHandlerFactory = new ProxyHandlerFactory();

            Assert.assertEquals(ProxyHandlerFactory.STORE_HANDLER_CONTEXT, proxyHandlerFactory.getContextFile());

            List<RequestHandler> handlers = null;

            handlers = proxyHandlerFactory.getIncomingRequestHandlerList();
            Assert.assertTrue("Expecting 2 handler that forward to other URLS",
                    handlers.size() == 1 && handlers.get(0) instanceof ProxyRepositoryRequestHandler);

            handlers = (List<RequestHandler>) proxyHandlerFactory.getContext().getBean("outgoingRequestHandlerList");
            Assert.assertTrue("Expecting 0 handler that forward to other URLS", handlers.size() == 0);
        } finally {
            ProxyProperties.setOverrideProperties(null);
        }
    }
}
