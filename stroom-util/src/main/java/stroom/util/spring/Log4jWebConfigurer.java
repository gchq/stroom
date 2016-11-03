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

package stroom.util.spring;

import org.springframework.core.io.Resource;
import org.springframework.util.Log4jConfigurer;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletContext;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Log4jWebConfigurer {
    public static final long LOG4J_REFRESH_MS = 10 * 1000;

    /**
     * Initialize log4j, including setting the web app root system property.
     *
     * @param servletContext
     *            the current ServletContext
     * @see WebUtils#setWebAppRootSystemProperty
     */
    public static void initLogging(ServletContext servletContext, Resource resource) {
        // Perform actual log4j initialization; else rely on log4j's default
        // initialization.
        try {
            String path = resource.getFile().getAbsolutePath();

            // Write log message to server log.
            servletContext.log("Initializing log4j from [" + path + "]");

            Log4jConfigurer.initLogging(path, LOG4J_REFRESH_MS);
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException("Invalid 'log4jConfigLocation' parameter: " + ex.getMessage());
        } catch (IOException ioEx) {
            throw new IllegalArgumentException("Invalid 'log4jConfigLocation' parameter: " + ioEx.getMessage());
        }
    }

    /**
     * Shut down log4j, properly releasing all file locks and resetting the web
     * app root system property.
     *
     * @param servletContext
     *            the current ServletContext
     * @see WebUtils#removeWebAppRootSystemProperty
     */
    public static void shutdownLogging(ServletContext servletContext) {
        servletContext.log("Shutting down log4j");
        Log4jConfigurer.shutdownLogging();
    }
}
