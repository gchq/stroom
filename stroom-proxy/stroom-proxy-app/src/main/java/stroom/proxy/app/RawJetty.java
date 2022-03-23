package stroom.proxy.app;

import stroom.util.io.ByteCountInputStream;
import stroom.util.io.StreamUtil;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RawJetty {
    public static void main(final String[] args) throws Exception {
        Server server = new Server(8090);
        server.setHandler(new DropRawHandler());
        server.start();
        server.join();
    }

    public static class DropRawHandler extends AbstractHandler {
//        private final AtomicLong counter = new AtomicLong();

        @Override
        public void handle(String s, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            try {
                try (final ByteCountInputStream inputStream = new ByteCountInputStream(request.getInputStream())) {
                    StreamUtil.streamToString(inputStream);
                }
                response.setStatus(HttpServletResponse.SC_OK);
            } catch (final Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }
}