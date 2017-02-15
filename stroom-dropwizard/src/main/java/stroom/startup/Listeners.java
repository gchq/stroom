package stroom.startup;

import io.dropwizard.setup.Environment;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import stroom.servlet.SessionListListener;
import stroom.util.spring.MyContextLoaderListener;

public class Listeners {
    static void loadInto(Environment environment, AnnotationConfigWebApplicationContext applicationContext){
        environment.servlets().addServletListeners(new MyContextLoaderListener(applicationContext));
        environment.servlets().addServletListeners(new SessionListListener());
    }
}
