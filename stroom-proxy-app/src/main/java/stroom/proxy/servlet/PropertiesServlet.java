package stroom.proxy.servlet;

import stroom.proxy.util.ProxyProperties;
import stroom.util.date.DateUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

public class PropertiesServlet extends HttpServlet {
    private static final long serialVersionUID = -3797089027443636243L;

    public final static String upDate = DateUtil.createNormalDateTimeString();

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        // Just forward to JSP but set the context to text
        resp.setContentType("text/html");
        resp.getWriter().println("<html>");
        resp.getWriter().println("<head>");
        resp.getWriter().println("<link type=\"text/css\" href=\"css/proxy.css\" rel=\"stylesheet\" />");
        resp.getWriter().println("</head>");
        resp.getWriter().println("<h1>proxy.properties</h1>");
        resp.getWriter().println("<body>");
        resp.getWriter().println("<table>");
        resp.getWriter().println("<tr>");
        resp.getWriter().println("<th>key</th><th>proxy.properties</th><th>default</th><th></th>");
        resp.getWriter().println("</tr>");
        final Properties defaultProperties = ProxyProperties.getDefaultProperties();
        final Properties properties = ProxyProperties.instance();

        if (defaultProperties != null) {
            final List<String> sortedKeySet = new ArrayList<String>();
            final Enumeration<Object> keyItr = defaultProperties.keys();
            while (keyItr.hasMoreElements()) {
                sortedKeySet.add((String) keyItr.nextElement());
            }
            Collections.sort(sortedKeySet);

            for (final String key : sortedKeySet) {
                String value = null;

                if (key.toLowerCase().contains("password")) {
                    value = "**********";
                } else {
                    if (properties != null) {
                        value = (String) properties.get(key);
                    }
                    if (value == null) {
                        value = (String) defaultProperties.get(key);
                    }
                }

                if (value == null) {
                    value = "";
                }

                resp.getWriter().print("<tr>");
                writeCell(resp.getWriter(), key);
                writeCell(resp.getWriter(), value);
                writeCell(resp.getWriter(), defaultProperties.getProperty(key));
                writeCell(resp.getWriter(), ProxyProperties.getPropertyDescriptionMap().get(key));
                resp.getWriter().print("</tr>");
            }
            resp.getWriter().println("</table>");
        }
        resp.getWriter().println("</body>");
        resp.getWriter().println("</html>");

    }

    private void writeCell(final PrintWriter writer, final String val) throws IOException {
        if (val == null) {
            writer.print("<td style=\"background-color:#CCC\"/>");
        } else {
            writer.print("<td>");
            writer.print(val);
            writer.print("</td>");
        }

    }
}
