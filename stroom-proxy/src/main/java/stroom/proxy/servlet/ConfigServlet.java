package stroom.proxy.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigServlet extends HttpServlet {
    private final String data;

    public ConfigServlet(final String path) {
        String data;
        try {
            final byte[] bytes = Files.readAllBytes(Paths.get(path));
            data = new String(bytes, Charset.forName("UTF-8"));
        } catch (final IOException e) {
            data = "Unable to read config";
        }
        this.data = data;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/plain");
        final Writer writer = response.getWriter();
        writer.write(data);
        writer.close();
    }
}
