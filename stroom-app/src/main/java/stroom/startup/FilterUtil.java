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

    public static FilterHolder addFilter(final ServletContextHandler servletContextHandler, Class<? extends Filter> clazz, String name) {
        return addFilter(servletContextHandler, clazz, name, MATCH_ALL_PATHS);
    }

    public static FilterHolder addFilter(final ServletContextHandler servletContextHandler, Class<? extends Filter> clazz, String name, final String urlPattern) {
        FilterHolder filterHolder = new FilterHolder(clazz);
        filterHolder.setName(name);

        servletContextHandler.addFilter(
                filterHolder,
                urlPattern,
                EnumSet.of(DispatcherType.REQUEST));

        return filterHolder;
    }
}
