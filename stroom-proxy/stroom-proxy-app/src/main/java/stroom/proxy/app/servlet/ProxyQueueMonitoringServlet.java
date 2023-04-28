package stroom.proxy.app.servlet;

import stroom.proxy.repo.dao.SqliteJooqHelper;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.shared.IsServlet;
import stroom.util.shared.Unauthenticated;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Unauthenticated
public class ProxyQueueMonitoringServlet extends HttpServlet implements IsServlet {

    private static final Set<String> PATH_SPECS = Set.of("/queues");

    private final Provider<QueueMonitors> queueMonitorsProvider;
    private final Provider<SqliteJooqHelper> sqliteJooqHelperProvider;
    private final Provider<FileStores> fileStoresProvider;

    @Inject
    public ProxyQueueMonitoringServlet(final Provider<QueueMonitors> queueMonitorsProvider,
                                       final Provider<SqliteJooqHelper> sqliteJooqHelperProvider,
                                       final Provider<FileStores> fileStoresProvider) {
        this.queueMonitorsProvider = queueMonitorsProvider;
        this.sqliteJooqHelperProvider = sqliteJooqHelperProvider;
        this.fileStoresProvider = fileStoresProvider;
    }


    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final QueueMonitors queueMonitors = queueMonitorsProvider.get();
        final Writer writer = response.getWriter();
        writer.write("<html>\n" +
                "<head>\n" +
                "<style>\n" +
                "body {\n" +
                "\tfont-family: arial, tahoma, verdana;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n");
        writer.write("<h1>Queues</h1>");
        writer.write(queueMonitors.log());
        writer.write("<h1>DB Records</h1>");
        writer.write(sqliteJooqHelperProvider.get().printTableRecordCounts());
        writer.write("<h1>File Stores</h1>");
        writer.write(fileStoresProvider.get().log());

        writer.write("</body>\n" +
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

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
