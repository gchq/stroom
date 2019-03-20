package stroom.dropwizard.common;

import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.guice.FilterInfo;

import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import java.util.EnumSet;
import java.util.Map;

public class Filters {
    private static final Logger LOGGER = LoggerFactory.getLogger(Filters.class);

    private final Environment environment;
    private final Map<FilterInfo, Filter> filters;

    @Inject
    Filters(final Environment environment, final Map<FilterInfo, Filter> filters) {
        this.environment = environment;
        this.filters = filters;
    }

    public void register() {
        final ServletContextHandler servletContextHandler = environment.getApplicationContext();

        LOGGER.info("Adding filters:");
        filters.forEach((filterInfo, filter) -> {
            final String name = filterInfo.getName();
            final String url = filterInfo.getUrlPattern();
            LOGGER.info("\t{} -> {}", name, url);

            final FilterHolder filterHolder = new FilterHolder(filter);
            filterHolder.setName(name);

            servletContextHandler.addFilter(
                    filterHolder,
                    filterInfo.getUrlPattern(),
                    EnumSet.of(DispatcherType.REQUEST));

            filterInfo.getInitParameters().forEach(filterHolder::setInitParameter);
        });
    }
}
