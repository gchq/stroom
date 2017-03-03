package stroom.startup;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.FilterHolder;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import java.util.EnumSet;
import java.util.Map;

public class Filters {

    private static final String MATCH_ALL_PATHS = "/*";

    private Environment environment;

    final FilterHolder loggingFilterHolder;
    final FilterHolder upgradeFilterHolder;
    final FilterHolder threadScopeContextFilterHolder;
    final FilterHolder rejectPostFilterHolder;
    final FilterHolder clusterCallCertificateRequiredFilterHolder;
    final FilterHolder exportCertificateRequiredFilterHolder;
    final FilterHolder shiroFilterHolder;

    public Filters(Environment environment) throws ClassNotFoundException {
        this.environment = environment;

        loggingFilterHolder = createFilter("stroom.util.logging.LoggingFilter", "loggingFilter", null);
        addFilter(loggingFilterHolder, MATCH_ALL_PATHS);

        upgradeFilterHolder = createFilter("stroom.util.upgrade.UpgradeDispatcherFilter", "upgradeFilter", null);
        addFilter(upgradeFilterHolder, MATCH_ALL_PATHS);

        threadScopeContextFilterHolder = createFilter("stroom.util.thread.ThreadScopeContextFilter", "threadScopeContextFilter", null);
        addFilter(threadScopeContextFilterHolder, MATCH_ALL_PATHS);

        rejectPostFilterHolder = createFilter("stroom.servlet.RejectPostFilter", "rejectPostFilter",
                ImmutableMap.<String, String>builder().put("rejectUri", "/").build());
        addFilter(rejectPostFilterHolder, MATCH_ALL_PATHS);

        clusterCallCertificateRequiredFilterHolder = createFilter("org.springframework.web.filter.DelegatingFilterProxy",
                "clusterCallCertificateRequiredFilter",
                ImmutableMap.<String, String>builder().put("contextAttribute", "org.springframework.web.servlet.FrameworkServlet.CONTEXT.spring").build());
        addFilter(clusterCallCertificateRequiredFilterHolder, "/clustercall.rpc");

        exportCertificateRequiredFilterHolder = createFilter("org.springframework.web.filter.DelegatingFilterProxy",
                "exportCertificateRequiredFilter",
                ImmutableMap.<String, String>builder().put("contextAttribute", "org.springframework.web.servlet.FrameworkServlet.CONTEXT.spring").build());
        addFilter(exportCertificateRequiredFilterHolder, "/export/*");

        shiroFilterHolder = createFilter("org.springframework.web.filter.DelegatingFilterProxy", "shiroFilter",
                ImmutableMap.<String, String>builder()
                        .put("contextAttribute", "org.springframework.web.servlet.FrameworkServlet.CONTEXT.spring")
                        .put("targetFilterLifecycle", "true").build());
        addFilter(shiroFilterHolder, MATCH_ALL_PATHS);
    }

    private FilterHolder createFilter(String clazz, String name, Map<String, String> initParams) throws ClassNotFoundException {
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

    private void addFilter(FilterHolder filterHolder, String urlPattern) throws ClassNotFoundException {
        environment.getApplicationContext().addFilter(
                filterHolder,
                urlPattern,
                EnumSet.of(DispatcherType.REQUEST));
    }
}
