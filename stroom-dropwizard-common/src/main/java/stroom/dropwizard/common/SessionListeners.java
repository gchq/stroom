package stroom.dropwizard.common;

import io.dropwizard.core.setup.Environment;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Set;
import javax.inject.Inject;

public class SessionListeners {

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
        sessionListeners.stream()
                .sorted(Comparator.comparing(sessionListener -> sessionListener.getClass().getName()))
                .forEach(sessionListener -> {
                    final String name = sessionListener.getClass().getName();
                    LOGGER.info("\t{}", name);
                    environment.servlets().addServletListeners(sessionListener);
                });
    }
}
