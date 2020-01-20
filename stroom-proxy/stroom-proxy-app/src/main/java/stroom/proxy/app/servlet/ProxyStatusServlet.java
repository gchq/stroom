package stroom.proxy.app.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.IsServlet;
import stroom.util.shared.Unauthenticated;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Public un-authenticated servlet for client systems to monitor stroom-proxy's availability.
 */
@Unauthenticated
public class ProxyStatusServlet extends HttpServlet implements IsServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyStatusServlet.class);

    private static final Set<String> PATH_SPECS = Set.of("/status");

    private final Provider<BuildInfo> buildInfoProvider;

    @Inject
    public ProxyStatusServlet(final Provider<BuildInfo> buildInfoProvider) {
        this.buildInfoProvider = buildInfoProvider;
    }

    @Override
    public void init() throws ServletException {
        LOGGER.info("Initialising Status Servlet");
        super.init();
        LOGGER.info("Initialised Status Servlet");
    }

    @Override
    public void destroy() {
        LOGGER.info("Destroying Status Servlet");
        super.destroy();
        LOGGER.info("Destroyed Status Servlet");
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {
        try (final PrintWriter printWriter = response.getWriter()) {
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setStatus(200);

            new ObjectMapper().writeValue(printWriter, buildInfoProvider.get());
        } catch (final IOException e) {
            LOGGER.error("Error retrieving stroom status", e);
            throw new ServletException("Error retrieving stroom status");
        }
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
