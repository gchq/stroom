package stroom.startup;

import io.dropwizard.setup.Environment;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import stroom.servlet.SessionListListener;

public class Listeners {
    static void loadInto(Environment environment, AnnotationConfigWebApplicationContext rootContext){
        environment.servlets().addServletListeners(new ContextLoaderListener(rootContext));
        environment.servlets().addServletListeners(new SessionListListener());
    }
}
