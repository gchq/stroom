/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.legacy.db;

import stroom.db.util.DataSourceFactory;
import stroom.db.util.DataSourceProxy;
import stroom.db.util.DbUtil;
import stroom.util.db.ForceCoreMigration;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Version;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MarkerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Configures anything related to persistence, e.g. transaction management, the
 * entity manager factory, data sources.
 * <p>
 * This does not extend {@link stroom.db.util.AbstractDataSourceProviderModule} as the core migrations
 * are special and need to happen first before all the other migrations. {@link ForceCoreMigration} is
 * used to achieve this by making all other datasource providers depend on {@link ForceCoreMigration}.
 */
@Deprecated
public class LegacyDbModule extends AbstractModule {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LegacyDbModule.class);

    private static final String MODULE = "stroom-legacy-db-migration";
    private static final String FLYWAY_LOCATIONS = "stroom/legacy/db/migration";
    private static final String FLYWAY_TABLE = "schema_version";

    private static final Map<DataSource, Set<String>> COMPLETED_MIGRATIONS = new ConcurrentHashMap<>();

    @Override
    protected void configure() {
        super.configure();

        // Force creation of connection provider so that legacy migration code executes.
        bind(ForceMigrationImpl.class).asEagerSingleton();

        // Allows other db modules to inject CoreMigration to ensure the core db migration
        // has run before they do
        bind(ForceCoreMigration.class).to(ForceMigrationImpl.class);

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(LegacyDbConnProvider.class);

        HasHealthCheckBinder.create(binder())
                .bind(DbHealthCheck.class);
    }

    @Provides
    @Singleton
    public LegacyDbConnProvider getConnectionProvider(final Provider<LegacyDbConfig> configProvider,
                                                      final DataSourceFactory dataSourceFactory) {
        LOGGER.debug(() -> "Getting connection provider for " + getModuleName());

        final DataSource dataSource = dataSourceFactory.create(configProvider.get());

        // Prevent migrations from being re-run for each test
        final boolean required = COMPLETED_MIGRATIONS
                .computeIfAbsent(dataSource, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(getModuleName());

        if (required) {
            performMigration(dataSource);
        }

        return createConnectionProvider(dataSource);
    }

    private Optional<Version> getVersionFromSchemaVersionTable(final Connection connection) {
        Optional<Version> optVersion = Optional.empty();
        try {
            try (final Statement statement = connection.createStatement()) {
                try (final ResultSet resultSet = statement.executeQuery(
                        "SELECT version " +
                                "FROM schema_version " +
                                "ORDER BY installed_rank DESC")) {
                    if (resultSet.next()) {
                        final String ver = resultSet.getString(1);
                        final String[] parts = ver.split("\\.");
                        int maj = 0;
                        int min = 0;
                        int pat = 0;
                        if (parts.length > 0) {
                            maj = Integer.parseInt(parts[0]);
                        }
                        if (parts.length > 1) {
                            min = Integer.parseInt(parts[1]);
                        }
                        if (parts.length > 2) {
                            pat = Integer.parseInt(parts[2]);
                        }

                        LOGGER.info("Found schema_version.version " + ver);
                        optVersion = Optional.of(new Version(maj, min, pat));
                    }

                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage());
            // Ignore.
        }
        return optVersion;
    }

    private Optional<Version> getVersionFromStroomVerTable(final Connection connection) {
        Optional<Version> optVersion = Optional.empty();
        try {
            try (final Statement statement = connection.createStatement()) {
                try (final ResultSet resultSet = statement.executeQuery(
                        "SELECT " +
                                "VER_MAJ, " +
                                "VER_MIN, " +
                                "VER_PAT " +
                                "FROM STROOM_VER " +
                                "ORDER BY " +
                                "VER_MAJ DESC, " +
                                "VER_MIN DESC, " +
                                "VER_PAT DESC " +
                                "LIMIT 1")) {
                    if (resultSet.next()) {
                        final Version version = new Version(
                                resultSet.getInt(1),
                                resultSet.getInt(2),
                                resultSet.getInt(3));
                        LOGGER.info("Found STROOM_VER.VER_MAJ/VER_MIN/VER_PAT " + version);
                        optVersion = Optional.of(version);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            // Ignore.
        }
        return optVersion;
    }

    private Optional<Version> getVersionFromPresenceOfFdTable(final Connection connection) {
        Optional<Version> optVersion = Optional.empty();
        try {
            try (final Statement statement = connection.createStatement()) {
                try (final ResultSet resultSet = statement.executeQuery(
                        "SELECT ID " +
                                "FROM FD " +
                                "LIMIT 1")) {
                    if (resultSet.next()) {
                        final Version version = new Version(2, 0, 0);
                        LOGGER.info("Found FD table so version is: " + version);
                        optVersion = Optional.of(version);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            // Ignore.
        }
        return optVersion;
    }

    private Optional<Version> getVersionFromPresenceOfFeedTable(final Connection connection) {
        Optional<Version> optVersion = Optional.empty();
        try {
            try (final Statement statement = connection.createStatement()) {
                try (final ResultSet resultSet = statement.executeQuery(
                        "SELECT ID " +
                                "FROM FEED " +
                                "LIMIT 1")) {
                    if (resultSet.next()) {
                        final Version version = new Version(2, 0, 0);
                        LOGGER.info("Found FEED table so version is: " + version);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            // Ignore.
        }
        return optVersion;
    }

    protected void performMigration(final DataSource dataSource) {

        final AtomicBoolean isDbUsingFlyWay = new AtomicBoolean(false);
        LOGGER.info("Testing installed Stroom schema version");

        final Optional<Version> optVersion = establishDbSchemaVersion(dataSource, isDbUsingFlyWay);

        optVersion.ifPresentOrElse(
                version -> LOGGER.info("Detected current Stroom version is v" + version.toString()),
                () -> LOGGER.info("This is a new installation. Legacy migrations won't be applied")
        );

        // Only apply legacy migrations if this is an old DB.
        if (optVersion.isPresent()) {
            Optional<String> optBaselineVersionAsString = optVersion.flatMap(version -> {
                if (!isDbUsingFlyWay.get()) {
                    if (version.getMajor() == 4 && version.getMinor() == 0 && version.getPatch() >= 60) {
                        // If Stroom is currently at v4.0.60+ then tell FlyWay to baseline at that version.
                        return Optional.of("4.0.60");
                    } else {
                        final String message =
                                "The current Stroom version cannot be upgraded to v5+. " +
                                        "You must be on v4.0.60 or later.";
                        LOGGER.error(MarkerFactory.getMarker("FATAL"), message);
                        throw new RuntimeException(message);
                    }
                } else {
                    return Optional.empty();
                }
            });

            final FluentConfiguration configuration = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(FLYWAY_LOCATIONS)
                    .table(FLYWAY_TABLE)
                    .baselineOnMigrate(true);

            optBaselineVersionAsString.ifPresent(configuration::baselineVersion);

            final Flyway flyway = configuration.load();

            optBaselineVersionAsString.ifPresent(ver ->
                    flyway.baseline());

            migrateDatabase(flyway);
        }
    }

    @NotNull
    private Optional<Version> establishDbSchemaVersion(final DataSource dataSource,
                                                       final AtomicBoolean isDbUsingFlyWay) {
        Optional<Version> optVersion;

        try (final Connection connection = dataSource.getConnection()) {

            isDbUsingFlyWay.set(DbUtil.doesTableExist(connection, "schema_version"));

            // Try a number of approaches to determine the version of the existing DB schema
            final Stream<Function<Connection, Optional<Version>>> versionFunctions = Stream.of(
                    this::getVersionFromSchemaVersionTable,
                    this::getVersionFromStroomVerTable,
                    this::getVersionFromPresenceOfFdTable,
                    this::getVersionFromPresenceOfFeedTable);

            optVersion = versionFunctions
                    .map(func -> func.apply(connection))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();

        } catch (final SQLException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return optVersion;
    }

    private void migrateDatabase(final Flyway flyway) {
        int pendingMigrations = flyway.info().pending().length;

        if (pendingMigrations > 0) {
            try {
                LOGGER.info("Applying {} Flyway DB migration(s) to {} in table {} from {}",
                        pendingMigrations,
                        MODULE,
                        FLYWAY_TABLE,
                        FLYWAY_LOCATIONS);
                flyway.migrate();
                LOGGER.info("Completed Flyway DB migration for {} in table {}",
                        getModuleName(),
                        FLYWAY_TABLE);
            } catch (FlywayException e) {
                LOGGER.error("Error migrating {} database", MODULE, e);
                throw e;
            }
        } else {
            LOGGER.info("No pending Flyway DB migration(s) for {} in {}",
                    MODULE,
                    FLYWAY_LOCATIONS);
        }
    }

    protected String getModuleName() {
        return MODULE;
    }


    protected LegacyDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements LegacyDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }

    private static class ForceMigrationImpl implements ForceCoreMigration {

        @Inject
        ForceMigrationImpl(@SuppressWarnings("unused") final LegacyDbConnProvider dataSource) {
            LOGGER.debug(() -> "Initialising " + this.getClass().getSimpleName());
        }
    }
}
