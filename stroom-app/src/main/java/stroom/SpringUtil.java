package stroom;

import com.google.common.base.Preconditions;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jetty.setup.ServletEnvironment;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.http.HttpSessionListener;
import java.util.EnumSet;

public class SpringUtil {
//    private static final Logger LOGGER = LoggerFactory.getLogger(SpringUtil.class);
//    private static final String HEALTH_CHECK_SUFFIX = "HealthCheck";

//    public static void addHealthCheck(final HealthCheckRegistry healthCheckRegistry, final ApplicationContext applicationContext, final Class<? extends HasHealthCheck> clazz) {
//        final HasHealthCheck hasHealthCheck = applicationContext.getBean(clazz);
//        String name = clazz.getName() + HEALTH_CHECK_SUFFIX;
//        LOGGER.debug("Registering heath check {}", name);
//        healthCheckRegistry.register(name, hasHealthCheck.getHealthCheck());
//    }

    public static void addFilter(final ServletContextHandler servletContextHandler, final ApplicationContext applicationContext, final Class<? extends Filter> clazz, final String url) {
        final Filter filter = applicationContext.getBean(clazz);
        final FilterHolder filterHolder = new FilterHolder(filter);
        filterHolder.setName(clazz.getSimpleName());

//        // Set params
//        if (initParams != null) {
//            for (Map.Entry<String, String> entry : initParams.entrySet()) {
//                filterHolder.setInitParameter(entry.getKey(), entry.getValue());
//            }
//        }

        servletContextHandler.addFilter(filterHolder, url, EnumSet.of(DispatcherType.REQUEST));
    }

    public static void addServlet(final ServletContextHandler servletContextHandler, final ApplicationContext applicationContext, final Class<?> clazz, final String url) {
        final Object bean = applicationContext.getBean(clazz);
        if (!(bean instanceof Servlet)) {
            throw new IllegalArgumentException("Expected servlet for bean " + clazz.getName());
        }
        final ServletHolder servletHolder = new ServletHolder(clazz.getSimpleName(), (Servlet) bean);
        servletContextHandler.addServlet(servletHolder, url);
    }

    public static void addServletListener(final ServletEnvironment servletEnvironment, final ApplicationContext applicationContext, final Class<? extends HttpSessionListener> clazz) {
        final HttpSessionListener httpSessionListener = applicationContext.getBean(clazz);
        servletEnvironment.addServletListeners(httpSessionListener);
    }

//    public static void addResource(final JerseyEnvironment jersey, final ApplicationContext applicationContext, final Class<?> clazz) {
//        final Object resource = applicationContext.getBean(clazz);
//        jersey.register(Preconditions.checkNotNull(resource));
//    }

    public static void manage(final LifecycleEnvironment lifecycleEnvironment, final ApplicationContext applicationContext, final Class<? extends Managed> clazz) {
        final Managed managed = applicationContext.getBean(clazz);
        lifecycleEnvironment.manage(managed);
    }
}
