package stroom.proxy.repo;

import stroom.proxy.repo.dao.SqliteJooqHelper;
import stroom.util.logging.Metrics;

import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class ProxyRepoDbMaintenance {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepoDbMaintenance.class);

    private final ProxyRepoDbConfig proxyDbConfig;
    private final SqliteJooqHelper jooq;

    @Inject
    public ProxyRepoDbMaintenance(final ProxyRepoDbConfig proxyDbConfig,
                                  final SqliteJooqHelper jooq) {
        this.proxyDbConfig = proxyDbConfig;
        this.jooq = jooq;
    }

    public List<Managed> getServices() {
        final List<Managed> services = new ArrayList<>();
        if (proxyDbConfig.getScheduledPragma() != null) {
            for (final ScheduledPragma scheduledPragma : proxyDbConfig.getScheduledPragma()) {
                services.add(new FrequencyExecutor(
                        "DB Maintenance",
                        () -> () -> pragma(scheduledPragma.getStatement()),
                        scheduledPragma.getFrequency().toMillis()));
            }
        }
        return services;
    }

    private void pragma(final String statement) {
        Metrics.measure("Executing DB Pragma: " + statement, () -> {
            LOGGER.info("Executing DB Pragma: " + statement);
            jooq.transaction(context -> context
                    .connection(connection -> connection
                            .prepareStatement(statement)
                            .execute()));
        });
    }
}
