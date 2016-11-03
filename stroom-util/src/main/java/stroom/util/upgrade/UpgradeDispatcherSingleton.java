/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.upgrade;

import stroom.util.logging.StroomLogger;
import stroom.util.spring.ContextAwareService;
import stroom.util.thread.ThreadScopeContextHolder;
import stroom.util.thread.ThreadUtil;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Old school singleton used along side UpgradeDispatcherServlet and
 * UpgradeDispatcherFilter to share state about the upgrade progress, and handle
 * requests.
 */
public class UpgradeDispatcherSingleton {
    private static StroomLogger LOGGER = StroomLogger.getLogger(UpgradeDispatcherSingleton.class);

    private Thread upgradeThread;
    private UpgradeDispatcherServlet servlet;
    private final AtomicBoolean contextStarted = new AtomicBoolean(false);
    private final AtomicBoolean dispatcherStarted = new AtomicBoolean(false);
    private boolean stop;

    private static UpgradeDispatcherSingleton instance = new UpgradeDispatcherSingleton();

    public static UpgradeDispatcherSingleton instance() {
        return instance;
    }

    private ServletConfig servletConfig = null;
//    private static final String UPGRADE_CLASS = "upgrade-class";

    /**
     * @return if OK to let calls in
     */
    public boolean isDispatcherStarted() {
        return dispatcherStarted.get();
    }

    /**
     * Handle incoming request (used in SERVLET OR FILTER)
     */
    public void service(final ServletRequest request, final ServletResponse response)
            throws IOException, ServletException {
//        if (upgradeHandler != null) {
//            UpgradeRequestUtil.service(request, response, upgradeHandler);
//        } else {
            UpgradeRequestUtil.writeHtmlHead(response.getWriter());
            response.getWriter().println("<h1>Starting Stroom</h1><p>Context Initialising...</p>");
            UpgradeRequestUtil.writeHtmlFooter(response.getWriter());
            response.getWriter().close();
//        }
    }

    public ServletConfig getServletConfig() {
        return servletConfig;
    }

    public UpgradeDispatcherServlet getServlet() {
        return servlet;
    }

    /**
     * Do the upgrade work and start the context
     */
    public void init(final UpgradeDispatcherServlet servlet, final ServletConfig servletConfig) {
        this.servlet = servlet;
        this.servletConfig = servletConfig;

//        if (servletConfig.getInitParameter(UPGRADE_CLASS) != null) {
//            try {
//                upgradeHandler = (UpgradeHandler) Class.forName(servletConfig.getInitParameter(UPGRADE_CLASS))
//                        .newInstance();
//            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
//                throw new RuntimeException(e);
//            }
//        }

        LOGGER.info("init() - Starting thread to run upgrade and then start context");

        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                    if (upgradeHandler != null) {
//                        LOGGER.info("init() - Running upgrade %s", upgradeHandler.getClass());
//                        if (!upgradeHandler.doUpgrade()) {
//                            LOGGER.warn("init() - Upgrade failed %s", upgradeHandler.getClass());
//                            // Don't start the context
//                            return;
//                        } else {
//                            LOGGER.info("init() - Upgrade passed %s", upgradeHandler.getClass());
//                        }
//                    }
                    // Don't start the context if we have been asked to shut
                    // down
                    if (stop) {
                        return;
                    }
                    LOGGER.info("init() - Starting spring context");
                    try {
                        ThreadScopeContextHolder.createContext();
                        getServlet().doInit(getServletConfig());
                    } finally {
                        ThreadScopeContextHolder.destroyContext();
                    }
                    LOGGER.info("init() - Spring context started");
                    contextStarted.set(true);

                    // If that went OK carry on
                    final WebApplicationContext context = getServlet().getWebApplicationContext();

                    // Then kick off the life cycle
                    for (final String beanName : context.getBeanNamesForType(ContextAwareService.class)) {
                        LOGGER.info("init() - Starting ContextAwareService %s", beanName);
                        ((ContextAwareService) context.getBean(beanName)).init();
                    }

                    dispatcherStarted.set(true);
                } catch (final Throwable ex) {
                    LOGGER.error("init() - Error during upgrade", ex);
                }
            }
        });
        thread.start();
        // Update our state reference
        upgradeThread = thread;
    }

    public void destroy() {
        LOGGER.info("destroy() - Stopping");

        stop = true;

//        // Tell upgrade handler
//        if (upgradeHandler != null) {
//            upgradeHandler.stop();
//        }

        // Wait for it to finish
        while (upgradeThread != null && upgradeThread.isAlive()) {
            LOGGER.info("destroy() - Waiting for upgrade to finish");
            ThreadUtil.sleep(1000);
        }

        // Upgrade thread done ... did we even start the spring context
        if (contextStarted.get()) {
            final WebApplicationContext context = getServlet().getWebApplicationContext();

            for (final String beanName : context.getBeanNamesForType(ContextAwareService.class)) {
                LOGGER.info("destroy() - Stopping ContextAwareService %s", beanName);
                ((ContextAwareService) context.getBean(beanName)).destroy();
            }

            LOGGER.info("destroy() - Stopping spring context");
            servlet.doDestroy();
        }
    }
}
