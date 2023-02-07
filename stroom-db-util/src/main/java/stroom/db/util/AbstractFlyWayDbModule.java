package stroom.db.util;

import stroom.config.common.AbstractDbConfig;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;

/**
 * @param <T_CONFIG>    A config class that extends {@link AbstractDbConfig}
 * @param <T_CONN_PROV> A class that extends {@link HikariDataSource}
 */
public abstract class AbstractFlyWayDbModule<T_CONFIG extends AbstractDbConfig, T_CONN_PROV extends DataSource>
        extends AbstractDataSourceProviderModule<T_CONFIG, T_CONN_PROV> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractFlyWayDbModule.class);

    protected abstract String getFlyWayTableName();

    protected abstract String getFlyWayLocation();

    // TODO: 06/02/2023 Refactor so we only have this method
    protected List<String> getFlyWayLocations() {
        return Collections.emptyList();
    }

    protected Optional<MigrationVersion> getMigrationTarget() {
        return Optional.empty();
    }

    @Override
    protected void configure() {
        super.configure();
        LOGGER.debug(() -> "Configure() called on " + this.getClass().getCanonicalName());
    }

    @Override
    protected void performMigration(final DataSource dataSource) {
        LOGGER.info(LogUtil.inBoxOnNewLine("Migrating database module: {}", getModuleName()));

        final String[] locations = Stream.concat(
                        Stream.of(getFlyWayLocation()),
                        NullSafe.nonNullList(getFlyWayLocations()).stream())
                .filter(Objects::nonNull)
                .distinct()
                .toArray(String[]::new);

        final FluentConfiguration fluentConfiguration = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .table(getFlyWayTableName())
                .baselineOnMigrate(true);

        // Set the target for the migration, i.e. only migrate up to this point.
        // Used for testing migrations.
        getMigrationTarget().ifPresent(fluentConfiguration::target);

        final Flyway flyway = fluentConfiguration.load();

        final String statesInfo = Arrays.stream(flyway.info().all())
                .collect(Collectors.groupingBy(MigrationInfo::getState))
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue().size())
                .collect(Collectors.joining(", "));

        try {
            LOGGER.info("{} - Validating existing and pending Flyway DB migration(s) ({}) " +
                            "using history table '{}' from path {}",
                    getModuleName(),
                    statesInfo,
                    getFlyWayTableName(),
                    getFlyWayLocation());

            // This will see if anything needs doing
            final int migrationsApplied = flyway.migrate();

            if (migrationsApplied > 0) {
                LOGGER.info("{} - Successfully applied {} Flyway DB migrations using history table '{}'",
                        getModuleName(),
                        migrationsApplied,
                        getFlyWayTableName());
            } else {
                LOGGER.info("{} - No Flyway DB migration(s) applied in path {}",
                        getModuleName(),
                        getFlyWayLocation());
            }

        } catch (FlywayException e) {
            LOGGER.error("{} - Error migrating database: {}", getModuleName(), e.getMessage(), e);
            throw e;
        }
    }
}
