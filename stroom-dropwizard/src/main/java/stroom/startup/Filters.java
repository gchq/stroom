package stroom.startup;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.FilterHolder;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import java.util.EnumSet;
import java.util.Map;

public class Filters {
    /**
     * Load all filters.
     */
    @SuppressWarnings("unchecked")
    static void loadInto(Environment environment) throws ClassNotFoundException {
        addFilter(environment, "log4jFilter", "stroom.util.logging.Log4JFilter", null, "/*");
        addFilter(environment, "upgradeFilter", "stroom.util.upgrade.UpgradeDispatcherFilter", null, "/*");
        addFilter(environment, "threadScopeContextFilter", "stroom.util.thread.ThreadScopeContextFilter", null, "/*");
        addFilter(environment, "rejectPostFilter", "stroom.servlet.RejectPostFilter",
                ImmutableMap.<String, String>builder().put("rejectUri", "/").build(), "/*");
        addFilter(environment, "clusterCallCertificateRequiredFilter", "org.springframework.web.filter.DelegatingFilterProxy",
                ImmutableMap.<String, String>builder().put("contextAttribute", "org.springframework.web.servlet.FrameworkServlet.CONTEXT.spring").build(),
                "/clustercall.rpc");
        addFilter(environment, "exportCertificateRequiredFilter", "org.springframework.web.filter.DelegatingFilterProxy",
                ImmutableMap.<String, String>builder().put("contextAttribute", "org.springframework.web.servlet.FrameworkServlet.CONTEXT.spring").build(),
                "/export/*");
        addFilter(environment, "shiroFilter", "org.springframework.web.filter.DelegatingFilterProxy",
                ImmutableMap.<String, String>builder()
                        .put("contextAttribute", "org.springframework.web.servlet.FrameworkServlet.CONTEXT.spring")
                        .put("targetFilterLifecycle", "true").build(),
                "/*");
    }

    private static void addFilter(Environment environment, String name, String clazz, Map<String, String> initParams, String urlPattern) throws ClassNotFoundException {
        environment.getApplicationContext().addFilter(
                createFilter(clazz, name, initParams),
                urlPattern,
                EnumSet.of(DispatcherType.REQUEST));
    }

    private static FilterHolder createFilter(String clazz, String name, Map<String, String> initParams) throws ClassNotFoundException {
        FilterHolder filterHolder = new FilterHolder((Class<? extends Filter>) Class.forName(clazz));
        filterHolder.setName(name);

        // Set params
        if (initParams != null) {
            for (Map.Entry<String, String> entry : initParams.entrySet()) {
                filterHolder.setInitParameter(entry.getKey(), entry.getValue());
            }
        }

        return filterHolder;
    }
}
