package stroom.startup;

import io.dropwizard.setup.Environment;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import stroom.servlet.SessionListListener;

public class Listeners {

    final ContextLoaderListener rootContextListener;
    final SessionListListener sessionListListener;

    public Listeners(Environment environment, AnnotationConfigWebApplicationContext rootContext){
        rootContextListener = new ContextLoaderListener(rootContext);
        environment.servlets().addServletListeners(rootContextListener);

        sessionListListener = new SessionListListener();
        environment.servlets().addServletListeners(sessionListListener);
    }
}
