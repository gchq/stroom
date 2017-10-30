package stroom.proxy.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StatusServlet extends HttpServlet {
    private static final long serialVersionUID = -3797089027443636243L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Just forward to JSP but set the context to text
        resp.setContentType("text/plain");
        getServletContext().getRequestDispatcher("/status.jsp").include(req, resp);
    }
}
