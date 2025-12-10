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

package stroom.proxy.app.servlet;

import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

@Unauthenticated
public class ProxyQueueMonitoringServlet extends HttpServlet implements IsServlet {

    private static final Set<String> PATH_SPECS = Set.of(
            ResourcePaths.addLegacyUnauthenticatedServletPrefix("/queues"));

    private final Provider<QueueMonitors> queueMonitorsProvider;
    private final Provider<FileStores> fileStoresProvider;

    @Inject
    public ProxyQueueMonitoringServlet(final Provider<QueueMonitors> queueMonitorsProvider,
                                       final Provider<FileStores> fileStoresProvider) {
        this.queueMonitorsProvider = queueMonitorsProvider;
        this.fileStoresProvider = fileStoresProvider;
    }


    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final QueueMonitors queueMonitors = queueMonitorsProvider.get();
        final Writer writer = response.getWriter();
        writer.write("<html>\n" +
                "<head>\n" +
                "<style>\n" +
                "body {\n" +
                "\tfont-family: arial, tahoma, verdana;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n");
        writer.write("<h1>Queues</h1>");
        writer.write(queueMonitors.log());
        writer.write("<h1>File Stores</h1>");
        writer.write(fileStoresProvider.get().log());

        writer.write("</body>\n" +
                "</html>");
        writer.close();
    }

    private String getURL(final HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
