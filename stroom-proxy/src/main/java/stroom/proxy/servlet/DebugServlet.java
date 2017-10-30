package stroom.proxy.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import stroom.util.web.DebugServletUtil;

public class DebugServlet extends HttpServlet {
    private static final long serialVersionUID = -3797089027443636243L;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        DebugServletUtil.doPost(req, resp);
    }
}
