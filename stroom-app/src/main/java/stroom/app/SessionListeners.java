package stroom.app;

import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpSessionListener;
import java.util.Set;

class SessionListeners {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionListeners.class);

    private final Environment environment;
    private final Set<HttpSessionListener> sessionListeners;

    @Inject
    SessionListeners(final Environment environment, final Set<HttpSessionListener> sessionListeners) {
        this.environment = environment;
        this.sessionListeners = sessionListeners;
    }

    public void register() {
        LOGGER.info("Adding session listeners:");
        for (HttpSessionListener sessionListener : sessionListeners) {
            final String name = sessionListener.getClass().getName();
            LOGGER.info("\t{}", name);
            environment.servlets().addServletListeners(sessionListener);
        }
    }
}
