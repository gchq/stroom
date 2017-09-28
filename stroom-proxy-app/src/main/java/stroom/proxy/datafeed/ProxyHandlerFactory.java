package stroom.proxy.datafeed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import stroom.proxy.handler.LocalFeedService;
import stroom.proxy.handler.RequestHandler;
import stroom.proxy.util.ProxyProperties;
import stroom.util.spring.PropertyConfigurer;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomStartup;
import stroom.util.thread.ThreadUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton bean that re-reads the proxy.properties and re-creates the app
 * context if the config has changed.
 */
public class ProxyHandlerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandlerFactory.class);

    private static final String COMMON_HANDLER_CONTEXT = "/META-INF/spring/stroomProxyCommonHandlerFactoryContext.xml";
    private static final String DATABASE_HANDLER_CONTEXT = "/META-INF/spring/stroomProxyDatabaseHandlerFactoryContext.xml";
    private static final String REMOTING_HANDLER_CONTEXT = "/META-INF/spring/stroomProxyRemotingHandlerFactoryContext.xml";
    private static final String REMOTING_CLIENT_CONTEXT = "/META-INF/spring/stroomRemoteClientContext.xml";

    private static final String LOG_REQUEST_HANDLER_CONTEXT = "/META-INF/spring/stroomProxyLogRequestHandlerFactoryContext.xml";
    static final String FORWARD_HANDLER_CONTEXT = "/META-INF/spring/stroomProxyForwardHandlerFactoryContext.xml";
    static final String STORE_HANDLER_CONTEXT = "/META-INF/spring/stroomProxyStoreHandlerFactoryContext.xml";
    static final String STORE_AND_FORWARD_HANDLER_CONTEXT = "/META-INF/spring/stroomProxyStoreAndForwardHandlerFactoryContext.xml";

    private static final String INCOMING_REQUEST_HANDLER_LIST = "incomingRequestHandlerList";

    private final ReentrantLock refreshLock = new ReentrantLock();
    private final Condition refreshCondition = refreshLock.newCondition();
    private final AtomicBoolean refreshThreadStop = new AtomicBoolean();
    private volatile Thread refreshThread;
    private volatile ClassPathXmlApplicationContext proxyHandlerContext;

    public String getContextFile() {
        if (ProxyProperties.isDefined(ProxyProperties.REPO_DIR)) {
            if (ProxyProperties.isDefined(ProxyProperties.FORWARD_URL)) {
                return STORE_AND_FORWARD_HANDLER_CONTEXT;
            } else {
                return STORE_HANDLER_CONTEXT;
            }
        } else {
            return FORWARD_HANDLER_CONTEXT;
        }
    }

    public List<RequestHandler> getIncomingRequestHandlerList() {
        return getRequestHandlerList(INCOMING_REQUEST_HANDLER_LIST);
    }

    public LocalFeedService getLocalFeedService() {
        return proxyHandlerContext.getBean(LocalFeedService.class);
    }

    @SuppressWarnings("unchecked")
    private List<RequestHandler> getRequestHandlerList(final String name) {
        final ApplicationContext proxyHandlerContext = getContext();
        final List<RequestHandler> forwardRequestHandlerList = (List<RequestHandler>) proxyHandlerContext.getBean(name);
        return forwardRequestHandlerList;
    }

    public ApplicationContext getContext() {
        if (proxyHandlerContext == null) {
            load();
        }
        return proxyHandlerContext;
    }

    static int instance = 0;

    private synchronized void startRefreshThread() {
        if (refreshThread == null) {
            refreshThreadStop.set(false);

            refreshThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    LOGGER.info("refreshThread() - Started");

                    refreshLock.lock();
                    try {
                        while (!refreshThreadStop.get()) {
                            try {
                                refreshCondition.await(10, TimeUnit.SECONDS);

                                if (!refreshThreadStop.get() && ProxyProperties.rescan()
                                        && ProxyProperties.validate()) {
                                    final ClassPathXmlApplicationContext toDeleteContext = proxyHandlerContext;
                                    // Any new requests will now be handled by
                                    // the new context.
                                    load();
                                    unload(toDeleteContext);
                                    LOGGER.info("refreshThread() - Rescan properties changed - reloaded");
                                }
                            } catch (final InterruptedException iEx) {
                                // This is OK
                                LOGGER.info("run() - " + iEx.getMessage());
                            } catch (final Exception e) {
                                LOGGER.error("run()", e);
                            }
                        }
                    } finally {
                        refreshLock.unlock();
                    }

                    LOGGER.info("refreshThread() - Finished");

                }
            });
            refreshThread.setName("ProxyProperties refresh thread " + (instance++));
            refreshThread.start();
        }
    }

    /**
     * Load the properties and create out configuration.
     */
    public synchronized void load() {
        final List<String> contextList = new ArrayList<String>();
        contextList.add(COMMON_HANDLER_CONTEXT);

        if (ProxyProperties.isDefined(ProxyProperties.REMOTING_URL)) {
            contextList.add(REMOTING_CLIENT_CONTEXT);
            contextList.add(REMOTING_HANDLER_CONTEXT);
        }
        if (ProxyProperties.isDefined(ProxyProperties.DB_REQUEST_VALIDATOR_JNDI_NAME)) {
            contextList.add(DATABASE_HANDLER_CONTEXT);
        }
        if (ProxyProperties.isDefined(ProxyProperties.LOG_REQUEST)) {
            contextList.add(LOG_REQUEST_HANDLER_CONTEXT);
        }
        contextList.add(getContextFile());

        // Load it ...
        final ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext(
                contextList.toArray(new String[contextList.size()]));
        classPathXmlApplicationContext.start();

        ProxyProperties.setDefaultProperties(
                classPathXmlApplicationContext.getBean(PropertyConfigurer.class).getDefaultProperties());

        // Swap in
        proxyHandlerContext = classPathXmlApplicationContext;
        LOGGER.info("load() - Loaded context " + getContextFile());
    }

    public synchronized void unload(final ClassPathXmlApplicationContext applicationContext) {
        if (applicationContext != null) {
            applicationContext.stop();
            applicationContext.destroy();
        }
    }

    @StroomStartup
    public synchronized void start() {
        load();
        startRefreshThread();
    }

    @StroomShutdown
    public synchronized void stop() {
        stopRefreshThread();
        unload(proxyHandlerContext);
    }

    private synchronized void stopRefreshThread() {
        if (refreshThread != null) {
            // Get the refresh thread to quit
            refreshThreadStop.set(true);

            refreshLock.lock();
            try {
                refreshCondition.signalAll();
            } finally {
                refreshLock.unlock();
            }

            while (refreshThread.isAlive()) {
                ThreadUtil.sleep(10);
            }
            refreshThread = null;
        }
    }

//    @Override
//    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
//        if (applicationContext instanceof WebApplicationContext) {
//            final WebApplicationContext webApplicationContext = (WebApplicationContext) applicationContext;
//            final ServletContext servletContext = webApplicationContext.getServletContext();
//            final String warName = ServletContextUtil.getWARName(servletContext);
//            ProxyProperties.setWebAppPropertiesName(warName);
//            PropertyConfigurer.setWebAppPropertiesName(warName);
//        }
//    }
}
