package stroom.dropwizard.common;

import stroom.util.guice.FilterInfo;

import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
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

        final int maxNameLength = filters.values().stream()
                .mapToInt(filter -> filter.getClass().getName().length())
                .max()
                .orElse(0);

        LOGGER.info("Adding filters:");
        filters.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().getClass().getName()))
                .forEach(entry -> {
                    final String className = entry.getValue().getClass().getName();
                    final String name = entry.getKey().getName();
                    final String url = entry.getKey().getUrlPattern();
                    LOGGER.info("\t{} => {}",
                            StringUtils.rightPad(className, maxNameLength, " "),
                            url);

                    final FilterHolder filterHolder = new FilterHolder(entry.getValue());
                    filterHolder.setName(name);

                    servletContextHandler.addFilter(
                            filterHolder,
                            url,
                            EnumSet.of(DispatcherType.REQUEST));

                    entry.getKey()
                            .getInitParameters()
                            .forEach(filterHolder::setInitParameter);
                });
    }
}
