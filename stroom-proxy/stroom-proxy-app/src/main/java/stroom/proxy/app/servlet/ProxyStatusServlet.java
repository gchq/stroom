package stroom.proxy.app.servlet;

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
import java.io.Writer;
import java.util.Set;

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
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/plain");
        final BuildInfo buildInfo = buildInfoProvider.get();
        final Writer writer = response.getWriter();
        writer.write("INFO,HTTP,OK");
        writer.write("\nINFO,STROOM_PROXY,Build version ");
        writer.write(buildInfo.getBuildVersion());
        writer.write("\nINFO,STROOM_PROXY,Build date ");
        writer.write(buildInfo.getBuildDate());
        writer.write("\nINFO,STROOM_PROXY,Up date ");
        writer.write(buildInfo.getUpDate());
        writer.close();
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
