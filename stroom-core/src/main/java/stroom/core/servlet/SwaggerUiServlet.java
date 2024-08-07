package stroom.core.servlet;

import stroom.config.common.UriFactory;
import stroom.ui.config.shared.UiConfig;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * The swagger specs are generated by the gradle task
 * ./gradlew :stroom-app:resolve
 * which places the stroom.json file in
 * stroom-app/src/main/resources/ui/noauth/swagger/
 */
@Unauthenticated
public class SwaggerUiServlet extends HttpServlet implements IsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwaggerUiServlet.class);

    static final String PATH_PART = "/swagger-ui";

    /**
     * Note: {@link RedirectServlet} will re-direct to here to support legacy servlet paths,
     * i.e. /stroom/noauth/swagger-ui/xxx
     */
    private static final Set<String> PATH_SPECS = Set.of(PATH_PART);

    private static final String TITLE = "@TITLE@";
    private static final String SWAGGER_SPEC_URL_TAG = "@SWAGGER_SPEC_URL@";
    private static final String SWAGGER_UI_REL_DIR_TAG = "@SWAGGER_UI_REL_DIR@";

    private final UriFactory uriFactory;
    private final UiConfig uiConfig;

    private String template;

    @Inject
    public SwaggerUiServlet(final UriFactory uriFactory,
                            final UiConfig uiConfig) {
        this.uriFactory = uriFactory;
        this.uiConfig = uiConfig;
    }

    @Override
    protected void doGet(final HttpServletRequest request,
                         final HttpServletResponse response) throws ServletException, IOException {
        try (final PrintWriter printWriter = response.getWriter()) {
            response.setContentType("text/html");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setStatus(200);

            String html = getHtmlTemplate();
            html = html.replace(TITLE, uiConfig.getHtmlTitle());
            html = html.replace(SWAGGER_SPEC_URL_TAG, getSwaggerSpecFileUrl());
            html = html.replace(SWAGGER_UI_REL_DIR_TAG, "ui/noauth/swagger-ui-dist");

            printWriter.write(html);
        } catch (final IOException e) {
            LOGGER.error("Error retrieving Swagger UI", e);
            throw new ServletException("Error retrieving stroom status");
        }
    }

    @Override
    public void init() throws ServletException {
        LOGGER.info("Initialising Swagger UI");
        super.init();
        LOGGER.info("Initialised Swagger UI");
    }

    @Override
    public void destroy() {
        LOGGER.info("Destroying Swagger UI");
        super.destroy();
        LOGGER.info("Destroyed Swagger UI");
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    private String getHtmlTemplate() {
        if (template == null) {
            final InputStream is = getClass().getResourceAsStream("swagger-ui-template.html");
            template = StreamUtil.streamToString(is);
            CloseableUtil.closeLogAndIgnoreException(is);
        }
        return template;
    }

    private String getSwaggerSpecFileUrl() {
        return uriFactory.publicUri(
                        ResourcePaths.addUnauthenticatedUiPrefix("swagger", "stroom.json"))
                .toString();
    }

}
