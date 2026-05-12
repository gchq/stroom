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

package stroom.dropwizard.common;

import stroom.util.ConsoleColour;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsAdminServlet;
import stroom.util.shared.NullSafe;

import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdminServlets {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminServlets.class);

    private static final String SERVLET_PATH_KEY = "servletPath";
    private static final String MENU_SERVLET_PATH = "/menu";

    private final Environment environment;
    private final Set<IsAdminServlet> adminServlets;

    @Inject
    AdminServlets(final Environment environment, final Set<IsAdminServlet> adminServlets) {
        this.environment = environment;
        this.adminServlets = adminServlets;
    }

    public void register() {
        final ServletContextHandler servletContextHandler = environment.getAdminContext();

        // Check for duplicate servlet path specs, assumes they are globally unique
        final List<String> duplicatePaths = adminServlets.stream()
                .flatMap(servlet ->
                        servlet.getPathSpecs().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!duplicatePaths.isEmpty()) {
            throw new RuntimeException(LogUtil.message(
                    "Multiple servlets exist for each of the following servlet paths [{}]",
                    String.join(", ", duplicatePaths)));
        }

        LOGGER.info("Adding servlets to admin path/port:");

        final Set<String> allPaths = new HashSet<>();

        final int maxNameLength = adminServlets.stream()
                .mapToInt(servlet -> servlet.getClass().getName().length())
                .max()
                .orElse(0);

        // Register all the path specs for each servlet class in pathspec order
        final List<AdminServletInfo> servletInfoList = adminServlets.stream()
                .flatMap(servlet ->
                        servlet.getPathSpecs().stream()
                                .map(pathSpec -> {
                                    final String name = servlet.getClass().getName();
                                    final String servletPath = Objects.requireNonNull(pathSpec);
                                    return new AdminServletInfo(servlet, name, servletPath);
                                }))
                .sorted(Comparator.comparing(AdminServletInfo::partialPathSpec))
                .toList();

        addMenuServlet(servletContextHandler, allPaths, maxNameLength, servletInfoList);

        servletInfoList.forEach(servletInfo ->
                addServlet(servletContextHandler, allPaths, maxNameLength, servletInfo));
    }

    private void addServlet(final ServletContextHandler servletContextHandler,
                            final Set<String> allPaths,
                            final int maxNameLength,
                            final AdminServletInfo adminServletInfo) {

        final String contextPath = servletContextHandler.getContextPath();
        final String fullPathSpec = contextPath + adminServletInfo.partialPathSpec;
        final String name = adminServletInfo.servletName;
        final IsAdminServlet isAdminServlet = adminServletInfo.adminServlet;
        if (allPaths.contains(fullPathSpec)) {
            LOGGER.error("\t{} => {}   {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    fullPathSpec,
                    ConsoleColour.red("**Duplicate path**"));
            throw new RuntimeException(LogUtil.message("Duplicate servlet path {}", fullPathSpec));
        } else {
            LOGGER.info("\t{} => {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    fullPathSpec);
        }

        final ServletHolder servletHolder;
        try {
            servletHolder = new ServletHolder(name, (Servlet) isAdminServlet);
        } catch (final ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Injected class {} is not a Servlet",
                    isAdminServlet.getClass().getName()));
        }
        servletContextHandler.addServlet(servletHolder, adminServletInfo.partialPathSpec);
        allPaths.add(fullPathSpec);
    }

    private void addMenuServlet(final ServletContextHandler servletContextHandler,
                                final Set<String> allPaths,
                                final int maxNameLength,
                                final List<AdminServletInfo> adminServletInfoList) {


        final String contextPath = servletContextHandler.getContextPath();
        final String fullPathSpec = contextPath + MENU_SERVLET_PATH;
        final String name = MenuServlet.class.getName();

        final MenuServlet menuServlet = new MenuServlet(contextPath, adminServletInfoList);

        if (allPaths.contains(fullPathSpec)) {
            //noinspection LoggingSimilarMessage
            LOGGER.error("\t{} => {}   {}",
                    StringUtils.rightPad(name, maxNameLength, " "),
                    fullPathSpec,
                    ConsoleColour.red("**Duplicate path**"));
            throw new RuntimeException(LogUtil.message("Duplicate servlet path {}", fullPathSpec));
        }
        //noinspection LoggingSimilarMessage
        LOGGER.info("\t{} => {}",
                StringUtils.rightPad(name, maxNameLength, " "),
                fullPathSpec);

        final ServletHolder servletHolder = new ServletHolder(name, menuServlet);
        servletContextHandler.addServlet(servletHolder, MENU_SERVLET_PATH);
        allPaths.add(fullPathSpec);
    }


    // --------------------------------------------------------------------------------


    private record AdminServletInfo(IsAdminServlet adminServlet,
                                    String servletName,
                                    String partialPathSpec) {

    }


    // --------------------------------------------------------------------------------


    private static class MenuServlet extends HttpServlet {

        private final String html;

        private MenuServlet(final String contextPath,
                            final List<AdminServletInfo> servletInfoList) {
            final List<AdminServletInfo> sortedInfo = NullSafe.stream(servletInfoList)
                    .sorted(Comparator.comparing(adminServletInfo ->
                            adminServletInfo.adminServlet.getDisplayName()))
                    .toList();

            final StringBuilder stringBuilder = new StringBuilder();
            writeHtmlHeader(stringBuilder);
            stringBuilder.append("<h1>Admin Servlets Menu</h1>");
            stringBuilder.append("<ul>");
            appendMenuItem(stringBuilder, "Dropwizard Admin Servlet", contextPath);

            for (final AdminServletInfo servletInfo : sortedInfo) {
                appendMenuItem(
                        stringBuilder,
                        servletInfo.adminServlet.getDisplayName(),
                        contextPath + servletInfo.partialPathSpec);
            }
            stringBuilder.append("</ul>");
            writeHtmlFooter(stringBuilder);
            html = stringBuilder.toString();
        }

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/html");
            resp.getWriter().print(html);
        }

        private static void appendMenuItem(final StringBuilder stringBuilder,
                                           final String name,
                                           final String url) {
            stringBuilder.append("<li>")
                    .append("<a href=\"")
                    .append(url)
                    .append("\">")
                    .append(name)
                    .append("</a>")
                    .append("</li>");
        }

        private static void writeHtmlHeader(final StringBuilder stringBuilder) {
            stringBuilder.append("""
                    <!DOCTYPE html>
                    <html>
                      <head>
                        <title>Admin Servlets Menu</title>
                        <style>
                          body {
                            font-family: arial, tahoma, verdana;
                          }
                          li {
                            line-height: 1.75em;
                          }
                          p {
                            margin-top: 0.5em;
                            margin-bottom: 0.5em;
                          }
                        </style>
                      </head>
                      <body>""");
        }

        private static void writeHtmlFooter(final StringBuilder stringBuilder) {
            stringBuilder.append("""
                      </body>
                    </html>""");
        }
    }
}
