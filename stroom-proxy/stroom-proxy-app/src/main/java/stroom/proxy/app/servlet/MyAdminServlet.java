package stroom.proxy.app.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MyAdminServlet extends HttpServlet {

    private String serviceName;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.serviceName = config.getInitParameter("service-name");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getContextPath() + req.getServletPath();
        resp.setStatus(200);
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        resp.setContentType("text/html");
        PrintWriter writer = resp.getWriter();

        try {
            String template = getResourceAsString("/admin.html", "UTF-8");
            String serviceName = this.serviceName == null
                    ? ""
                    : " (" + this.serviceName + ")";

            writer.println(MessageFormat.format(template, new Object[]{path, serviceName}));
        } finally {
            writer.close();
        }
    }

    String getResourceAsString(String resource, String charSet) throws IOException {
        InputStream in = this.getClass().getResourceAsStream(resource);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return out.toString(charSet);
    }
}