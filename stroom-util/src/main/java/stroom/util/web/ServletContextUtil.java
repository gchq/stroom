/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

public class ServletContextUtil {

    private static final String DEFAULT_NAME = "stroom";
    private static final String WEBAPP = "ui";

    public static final String getWARName(final ServletConfig servletConfig) {
        if (servletConfig == null) {
            return DEFAULT_NAME;
        }

        return getWARName(servletConfig.getServletContext());
    }

    public static final String getWARName(final ServletContext servletContext) {
        final String fullPathUnavailableMessage = "[Full path is not available]";
        String fullPath = null;
        // Servlet context might not yet be loaded
        if (servletContext != null) {
            fullPath = servletContext.getRealPath(".");
            if (fullPath != null) {
                final String[] parts = fullPath.split("/");

                if (WEBAPP.equals(parts[parts.length - 1])) {
                    return DEFAULT_NAME;
                }

                return parts[parts.length - 2];
            } else {
                return fullPathUnavailableMessage;
            }
        } else {
            return fullPathUnavailableMessage;
        }

    }
}
