package stroom.proxy.app.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.BuildInfoProvider;
import stroom.util.shared.BuildInfo;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

public class ProxyStatusServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyStatusServlet.class);

    private final BuildInfoProvider buildInfoProvider;

    @Inject
    public ProxyStatusServlet(final BuildInfoProvider buildInfoProvider) {
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
}
