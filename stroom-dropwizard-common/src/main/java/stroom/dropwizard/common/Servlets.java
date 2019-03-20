package stroom.dropwizard.common;

import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.guice.ServletInfo;

import javax.inject.Inject;
import javax.servlet.Servlet;
import java.util.Map;

public class Servlets {
    private static final Logger LOGGER = LoggerFactory.getLogger(Servlets.class);

    private final Environment environment;
    private final Map<ServletInfo, Servlet> servlets;

    @Inject
    Servlets(final Environment environment, final Map<ServletInfo, Servlet> servlets) {
        this.environment = environment;
        this.servlets = servlets;
    }

    public void register() {
        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        LOGGER.info("Adding servlets:");
        servlets.forEach((servletInfo, servlet) -> {
            final String name = servlet.getClass().getSimpleName();
            final String url = servletInfo.getUrl();
            LOGGER.info("\t{} -> {}", name, url);
            final ServletHolder servletHolder = new ServletHolder(name, servlet);
            servletContextHandler.addServlet(servletHolder, url);
        });
    }
}
