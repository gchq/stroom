package stroom.proxy.servlet;

import stroom.util.BuildInfoUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

public class ProxyWelcomeServlet extends HttpServlet {
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final Writer writer = response.getWriter();
        writer.write("<html>\n" +
                "<head>\n" +
                "<style>\n" +
                "body {\n" +
                "\tfont-family: arial, tahoma, verdana;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<h1>Stroom Proxy " +
                BuildInfoUtil.getBuildVersion() +
                " built on " +
                BuildInfoUtil.getBuildDate() +
                "</h1>\n" +
                "\n" +
                "<p>Send data to " +
                getURL(request) +
                "datafeed</p>\n" +
                "\n" +
                "</body>\n" +
                "</html>");
        writer.close();
    }

    private String getURL(final HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }
}
