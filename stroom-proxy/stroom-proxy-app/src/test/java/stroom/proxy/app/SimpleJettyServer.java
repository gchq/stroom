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
