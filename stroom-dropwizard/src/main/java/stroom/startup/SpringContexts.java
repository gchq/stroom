package stroom.startup;

import io.dropwizard.setup.Environment;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import stroom.Config;

public class SpringContexts {

    final AnnotationConfigWebApplicationContext applicationContext;
    final AnnotationConfigWebApplicationContext rootContext;

    public SpringContexts() throws ClassNotFoundException {
        rootContext = new AnnotationConfigWebApplicationContext();

        applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.setParent(rootContext);
        applicationContext.registerShutdownHook();

        // We don't need to register @Configuration classes here because they're loaded in SpringContexts.newUpgradeDispatcherServlet(...)
    }

    public void start(Environment environment, Config configuration){
        // We need to set the servlet context otherwise there will be no default servlet handling.
        applicationContext.setServletContext(environment.getApplicationContext().getServletContext());

        rootContext.refresh();
        rootContext.start();
        applicationContext.refresh();
        applicationContext.getBeanFactory().registerSingleton("dwConfiguration", configuration);
        applicationContext.getBeanFactory().registerSingleton("dwEnvironment", environment);
        applicationContext.getBeanFactory().registerSingleton("dwObjectMapper", environment.getObjectMapper());
        applicationContext.start();
    }
}
