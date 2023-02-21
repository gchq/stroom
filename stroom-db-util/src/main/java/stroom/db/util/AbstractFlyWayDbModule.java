package stroom.db.util;

import stroom.config.common.AbstractDbConfig;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.util.Collections;
import java.util.List;
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

    /**
     * Provide a list of locations that flyway can look for migration scripts/classes in.
     * See {@link FluentConfiguration#locations(String...)}.
     */
    protected List<String> getFlyWayLocations() {
        return Collections.emptyList();
    }

    /**
     * @return An optional target version for flyway to migrate up to (inclusive).
     * Intended to be overridden only by classes testing specific migrations.
     */
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
        FlywayUtil.migrate(dataSource,
                getFlyWayLocations(),
                getMigrationTarget().orElse(null),
                getFlyWayTableName(),
                getModuleName());
    }

    /**
     * Combine two lists of flyway migration locations
     */
    protected static List<String> mergeLocations(
            final List<String> locations1,
            final List<String> locations2) {
        return Stream.concat(
                        NullSafe.list(locations1).stream(),
                        NullSafe.list(locations2).stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Combine two lists of flyway migration locations
     */
    protected static List<String> mergeLocations(
            final List<String> locations1,
            final String location2) {
        return Stream.concat(
                        NullSafe.list(locations1).stream(),
                        Stream.of(location2))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
