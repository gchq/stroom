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

package stroom.core.servlet;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Re-directs legacy paths to their new locations.
 * /stroom/dashboard => /dashboard
 * /stroom/noauth/swagger-ui => /swagger-ui
 */
public class RedirectServlet extends HttpServlet implements IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RedirectServlet.class);

    private static final Pattern REPLACE_PATTERN = Pattern.compile(
            "^" + ResourcePaths.SERVLET_BASE_PATH + "(" + ResourcePaths.NO_AUTH + ")?");

    private static final Set<String> PATH_SPECS = Set.of(
            ResourcePaths.addLegacyAuthenticatedServletPrefix(DashboardServlet.PATH_PART),
            ResourcePaths.addLegacyUnauthenticatedServletPrefix(SwaggerUiServlet.PATH_PART)
    );

    static {
        final String conversions = Stream.of(
                        ResourcePaths.addLegacyAuthenticatedServletPrefix(DashboardServlet.PATH_PART),
                        ResourcePaths.addLegacyUnauthenticatedServletPrefix(SwaggerUiServlet.PATH_PART))
                .map(legacyPath -> legacyPath + " => " + stripLegacyParts(legacyPath))
                .collect(Collectors.joining("\n"));

        LOGGER.info("Setting up servlet re-directs:\n{}", conversions);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {

        final String servletPath = req.getServletPath();
        final String newUri = convertServletPath(req, resp, servletPath);

        LOGGER.debug("Re-directing req: {} to newUri: {}", req, newUri);
        resp.sendRedirect(newUri);
    }

    private static String convertServletPath(final HttpServletRequest req,
                                             final HttpServletResponse resp,
                                             final String servletPath) {
        String newUri = stripLegacyParts(servletPath);

        // Add on any params
        final Map<String, String[]> parameterMap = req.getParameterMap();
        if (!parameterMap.isEmpty()) {
            newUri += "?";
            final String paramStr = parameterMap.entrySet().stream()
                    .flatMap(entry -> Arrays.stream(entry.getValue())
                            .map(val -> Map.entry(entry.getKey(), val)))
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));
            newUri += paramStr;
            newUri = resp.encodeRedirectURL(newUri);
        }
        return newUri;
    }

    private static String stripLegacyParts(final String servletPath) {
        return REPLACE_PATTERN.matcher(servletPath).replaceAll("");
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
