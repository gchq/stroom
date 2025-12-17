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

package stroom.proxy.app;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

import java.io.IOException;

public class SimpleJettyServer {

    public static void main(final String[] args) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(8090);
        server.setConnectors(new Connector[]{connector});

        final ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(
                BlockingServlet.class,
                "/stroom/noauth/datafeed");
        server.setHandler(servletHandler);
        System.out.println("Starting Jetty " + server.getThreadPool().getThreads());

        server.start();
        server.join();
    }

    public static class BlockingServlet extends HttpServlet {

        @Override
        protected void doPost(final HttpServletRequest request,
                              final HttpServletResponse response)
                throws ServletException, IOException {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("{ \"status\": \"ok\"}");
        }

//        protected void doGet(
//                HttpServletRequest request,
//                HttpServletResponse response)
//                throws ServletException, IOException {
//
//            response.setContentType("application/json");
//            response.setStatus(HttpServletResponse.SC_OK);
//            response.getWriter().println("{ \"status\": \"ok\"}");
//        }
    }
}
