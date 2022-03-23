package stroom.proxy.app;

import stroom.util.io.ByteCountInputStream;
import stroom.util.io.StreamUtil;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RawJettyApplication {
    public static void main(final String[] args) throws Exception {
//        Server server = new Server(8090);
        System.out.println("AVAILABLE PROCESSORS = " + Runtime.getRuntime().availableProcessors());

        final Server server = new Server(new QueuedThreadPool(200, 50));
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8090);
        server.setConnectors(new Connector[]{connector});


        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        final DropRawServlet servlet = new DropRawServlet();
        handler.addServletWithMapping(new ServletHolder(servlet), "/stroom/noauth/datafeed");
        server.start();
        server.join();
    }

    public static class DropRawServlet extends HttpServlet {
        private final AtomicLong counter = new AtomicLong();

        public DropRawServlet() {
        }

        @Override
        protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
            try {
                try (final ByteCountInputStream inputStream = new ByteCountInputStream(req.getInputStream())) {
                    StreamUtil.streamToString(inputStream);
                }
                resp.setStatus(HttpServletResponse.SC_OK);
            } catch (final Exception e) {
                System.err.println(e.getMessage());
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//            final String name = Strings.nullToEmpty(req.getParameter("name"));
//            resp.setContentType("application/json");
//
//            if (name.length() > 5) {
//                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//                try (final JsonGenerator json = jsonFactory.createGenerator(resp.getOutputStream())) {
//                    json.writeStartObject();
//                    json.writeStringField("error", "Query parameter name must be shorter than 5 letters");
//                    json.writeEndObject();
//                    return;
//                }
//            }
//
//            resp.setStatus(HttpServletResponse.SC_OK);
//            try (final JsonGenerator json = jsonFactory.createGenerator(resp.getOutputStream())) {
//                json.writeStartObject();
//                json.writeNumberField("id", counter.incrementAndGet());
//                json.writeStringField("content", String.format(template, name.length() != 0 ? name : defaultName));
//                json.writeEndObject();
//            }
        }
    }
}