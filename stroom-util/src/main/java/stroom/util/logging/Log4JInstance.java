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

package stroom.util.logging;

import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;
import stroom.util.config.StroomProperties;
import stroom.util.spring.Log4jWebConfigurer;
import stroom.util.spring.StroomResourceLoaderUtil;
import stroom.util.web.ServletContextUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * start / stop / refresh Log4J configuration
 */
public class Log4JInstance {
    private static final long serialVersionUID = 8833402961986851281L;

    private static final String LOG4J = "log4j";
    private static final String DEFAULT_LOG4J = "classpath:log4j.xml";
    private static Log4JInstance instance;
    private final AtomicInteger initCount = new AtomicInteger();
    private transient volatile ServletContext servletContext;
    private transient volatile StroomLogger logger;
    private transient volatile ResourceLoader resourceLoader;

    public synchronized static Log4JInstance getInstance() {
        if (instance == null) {
            instance = new Log4JInstance();
        }
        return instance;
    }

    public void init(final ServletContext servletContext) throws ServletException {
        final int count = initCount.getAndIncrement();
        if (count == 0) {
            resourceLoader = new DefaultResourceLoader();

            logInfo("init() - Starting log4j");

            String log4jParam = servletContext.getInitParameter(LOG4J);
            if (log4jParam == null) {
                log4jParam = DEFAULT_LOG4J;
            }

            final String warName = ServletContextUtil.getWARName(servletContext);

            logInfo("init() - WAR " + warName);

            final Iterator<String> prefixPartsItr = Arrays.asList(warName + ".", "").iterator();
            final List<String> log4jParts = Arrays.asList(log4jParam.split(","));

            boolean done = false;
            while (prefixPartsItr.hasNext() && !done) {
                final String prefixPart = prefixPartsItr.next();
                final Iterator<String> log4jPartsItr = log4jParts.iterator();
                while (log4jPartsItr.hasNext() && !done) {
                    final String log4jPart = log4jPartsItr.next();

                    String pathToTry = null;
                    final int splitPos = log4jPart.indexOf(":") + 1;
                    // Keep the protocol part classpath: but add the prefix
                    if (splitPos > 0) {
                        pathToTry = log4jPart.substring(0, splitPos) + prefixPart + log4jPart.substring(splitPos);
                    } else {
                        pathToTry = prefixPart + log4jPart;
                    }

                    done = tryConfig(servletContext, pathToTry);
                }
            }
            if (!done) {
                logError("init() - Log4j configuration not found " + log4jParam + " with or without prefix " + warName);
            }
        }
    }

    private synchronized boolean tryConfig(final ServletContext servletContext, final String location) {
        final String path = StroomProperties.replaceProperties(location);

        logInfo("tryConfig() - " + location + " -> " + path);

        final Resource resource = StroomResourceLoaderUtil.getResource(resourceLoader, path);
        boolean existingFile = false;
        try {
            logInfo("tryConfig() - " + resource + " exists = " + resource.exists() + " url = " + resource.getURI());
            existingFile = resource.exists() && ResourceUtils.isFileURL(resource.getURL());

        } catch (final Exception ex) {
            logError("tryConfig() - " + ex.getMessage());
        }
        if (existingFile) {
            BasicConfigurator.resetConfiguration();
            Log4jWebConfigurer.initLogging(servletContext, resource);
            logInfo("tryConfig() - Started log4j using: " + path + " (" + resource.getFilename() + ")");

            logger = StroomLogger.getLogger(Log4JInstance.class);
            logger.info("tryConfig() - Started log4j using: " + path + " (" + resource.getFilename() + ")");

            boolean redirectSystemOut = true;
            final Enumeration<?> allApenders = Logger.getRootLogger().getAllAppenders();
            while (allApenders.hasMoreElements()) {
                final Appender appender = (Appender) allApenders.nextElement();
                if (appender.getClass().getName().contains("Console")) {
                    redirectSystemOut = false;
                }
            }

            if (redirectSystemOut) {
                System.setErr(LoggerPrintStream.create(logger, true));
                System.setOut(LoggerPrintStream.create(logger, true));
            }

            return true;
        } else {
            logInfo("tryConfig() - Not found: " + path);
        }

        return false;
    }

    public void destroy(final ServletContext servletContext) {
        final int count = initCount.decrementAndGet();
        if (count == 0) {
            logInfo("destroy() - LOG4J SHUTDOWN");
            Log4jWebConfigurer.shutdownLogging(servletContext);
            logger = null;
        }
    }

    private void logInfo(final String message) {
        if (servletContext != null) {
            servletContext.log(message);
        }
        if (logger != null) {
            logger.info(message);
        }
    }

    private void logError(final String message) {
        if (servletContext != null) {
            servletContext.log(message);
        }
        if (logger != null) {
            logger.error(message);
        }
    }

}
