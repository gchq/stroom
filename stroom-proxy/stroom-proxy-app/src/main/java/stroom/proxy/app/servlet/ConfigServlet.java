package stroom.proxy.app.servlet;

import com.codahale.metrics.health.HealthCheck;
import stroom.util.HasHealthCheck;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigServlet extends HttpServlet implements HasHealthCheck {
    private static String PATH;
    private final String data;

    public ConfigServlet() {
        String data;
        try {
            final byte[] bytes = Files.readAllBytes(Paths.get(PATH));
            data = new String(bytes, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            data = "Unable to read config";
        }
        this.data = data;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");
        final Writer writer = response.getWriter();
        writer.write(data);
        writer.close();
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.builder()
                .healthy()
                .withDetail("path", super.getServletContext().getContextPath())
                .build();
    }

    public static void setPath(final String PATH) {
        ConfigServlet.PATH = PATH;
    }
}
