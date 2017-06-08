package stroom.startup;

import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import stroom.util.upgrade.UpgradeDispatcherServlet;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ServletMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServletMonitor.class);

    private final ServletHolder upgradeDispatcherServletHolder;
    private final List<Consumer<ApplicationContext>> applicationContextListeners = new ArrayList<>();
    private AtomicReference<ApplicationContext> applicationContextRef = new AtomicReference<>();

    public ServletMonitor(final ServletHolder upgradeDispatcherServletHolder) {

        this.upgradeDispatcherServletHolder = upgradeDispatcherServletHolder;

        //initiate a thread to wait for the web application to be fully started
        CompletableFuture.runAsync(this::waitForApplicationContext);
    }

    public synchronized void registerApplicationContextListener(final Consumer<ApplicationContext> listener) {
        if (applicationContextRef.get() == null) {
            applicationContextListeners.add(listener);
        } else {
            //context is ready to notify the listener immediately
            listener.accept(applicationContextRef.get());
        }
    }

    private void waitForApplicationContext() {
        LOGGER.debug("Waiting for web application context");
        while (applicationContextRef.get() == null) {
            try {
                // This checks to see if the servlet has started. It'll throw an exception if it has.
                // I don't know of another way to check to see if it's ready.
                // If we try and get the servlet manually it'll fail to initialise because it won't have the ServletContext;
                // i.e. we need to let the Spring lifecycle stuff take care of this for us.
                upgradeDispatcherServletHolder.ensureInstance();

                UpgradeDispatcherServlet servlet = (UpgradeDispatcherServlet) upgradeDispatcherServletHolder.getServlet();
                applicationContextRef.set(servlet.getWebApplicationContext());

            } catch (ServletException e) {
                // This could be an UnavailableException, caused by ensureInstance().
                // We don't care, we're going to keep trying.
            }
        }
        LOGGER.debug("Web application context acquired, notifying listeners");
        synchronized (this) {
            applicationContextListeners.forEach(listener -> listener.accept(applicationContextRef.get()));
        }
    }


}
