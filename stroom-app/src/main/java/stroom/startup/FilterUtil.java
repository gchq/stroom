package stroom.startup;

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

    public static FilterHolder addFilter(final ServletContextHandler servletContextHandler,
                                         final Class<? extends Filter> clazz,
                                         final String name,
                                         final Map<String, String> initParams) {
        return addFilter(servletContextHandler, clazz, name, MATCH_ALL_PATHS, initParams);
    }

    public static FilterHolder addFilter(final ServletContextHandler servletContextHandler,
                                         final Class<? extends Filter> clazz,
                                         final String name,
                                         final String urlPattern,
                                         final Map<String, String> initParams) {
        FilterHolder filterHolder = new FilterHolder(clazz);
        filterHolder.setName(name);

        servletContextHandler.addFilter(
                filterHolder,
                urlPattern,
                EnumSet.of(DispatcherType.REQUEST));

        if (initParams != null) {
            filterHolder.setInitParameters(initParams);
        }

        return filterHolder;
    }
}
