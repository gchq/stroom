package stroom.module;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import java.util.EnumSet;
import java.util.Map;

public final class FilterUtil {
    private static final String MATCH_ALL_PATHS = "/*";

    private FilterUtil() {
    }

    public static void addFilter(final ServletContextHandler servletContextHandler, Class<? extends Filter> clazz, String name, Map<String, String> initParams) {
        addFilter(servletContextHandler, clazz, name, initParams, MATCH_ALL_PATHS);
    }

    public static void addFilter(final ServletContextHandler servletContextHandler, Class<? extends Filter> clazz, String name, Map<String, String> initParams, final String urlPattern) {
        FilterHolder filterHolder = new FilterHolder(clazz);
        filterHolder.setName(name);

        // Set params
        if (initParams != null) {
            for (Map.Entry<String, String> entry : initParams.entrySet()) {
                filterHolder.setInitParameter(entry.getKey(), entry.getValue());
            }
        }

        servletContextHandler.addFilter(
                filterHolder,
                urlPattern,
                EnumSet.of(DispatcherType.REQUEST));
    }
}
