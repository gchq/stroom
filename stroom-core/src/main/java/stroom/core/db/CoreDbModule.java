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

package stroom.core.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.db.util.AbstractDataSourceProviderModule;
import stroom.db.util.DataSourceProxy;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Version;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Configures anything related to persistence, e.g. transaction management, the
 * entity manager factory, data sources.
 */
public class CoreDbModule extends AbstractDataSourceProviderModule<CoreConfig, CoreDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreDbModule.class);

    private static final String MODULE = "stroom-core";
    private static final String FLYWAY_LOCATIONS = "stroom/core/db/migration/mysql";
    private static final String FLYWAY_TABLE = "schema_version";

    @Override
    protected void configure() {
        super.configure();

        // Force creation of connection provider so that legacy migration code executes.
        bind(ForceMigration.class).asEagerSingleton();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(CoreDbConnProvider.class);

        TaskHandlerBinder.create(binder())
                .bind(FindSystemTableStatusAction.class, FindSystemTableStatusHandler.class);
    }

    @Override
    protected void performMigration(final DataSource dataSource) {
        String baselineVersionAsString = null;
        Version version = null;
        boolean usingFlyWay = false;
        LOGGER.info("Testing installed Stroom schema version");

        try (final Connection connection = dataSource.getConnection()) {
            try {
                try (final Statement statement = connection.createStatement()) {
                    try (final ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version ORDER BY installed_rank DESC")) {
                        if (resultSet.next()) {
                            usingFlyWay = true;

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

                            version = new Version(maj, min, pat);
                            LOGGER.info("Found schema_version.version " + ver);
                        }
                    }
                }
            } catch (final SQLException e) {
                LOGGER.debug(e.getMessage());
                // Ignore.
            }

            if (version == null) {
                try {
                    try (final Statement statement = connection.createStatement()) {
                        try (final ResultSet resultSet = statement.executeQuery("SELECT VER_MAJ, VER_MIN, VER_PAT FROM STROOM_VER ORDER BY VER_MAJ DESC, VER_MIN DESC, VER_PAT DESC LIMIT 1")) {
                            if (resultSet.next()) {
                                version = new Version(resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3));
                                LOGGER.info("Found STROOM_VER.VER_MAJ/VER_MIN/VER_PAT " + version);
                            }
                        }
                    }
                } catch (final SQLException e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Ignore.
                }
            }

            if (version == null) {
                try {
                    try (final Statement statement = connection.createStatement()) {
                        try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FD LIMIT 1")) {
                            if (resultSet.next()) {
                                version = new Version(2, 0, 0);
                            }
                        }
                    }
                } catch (final SQLException e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Ignore.
                }
            }

            if (version == null) {
                try {
                    try (final Statement statement = connection.createStatement()) {
                        try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FEED LIMIT 1")) {
                            if (resultSet.next()) {
                                version = new Version(2, 0, 0);
                            }
                        }
                    }
                } catch (final SQLException e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Ignore.
                }
            }
        } catch (final SQLException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        if (version != null) {
            LOGGER.info("Detected current Stroom version is v" + version.toString());
        } else {
            LOGGER.info("This is a new installation!");
        }

        if (version != null && !usingFlyWay) {
            if (version.getMajor() == 4 && version.getMinor() == 0 && version.getPatch() >= 60) {
                // If Stroom is currently at v4.0.60+ then tell FlyWay to baseline at that version.
                baselineVersionAsString = "4.0.60";
            } else {
                final String message = "The current Stroom version cannot be upgraded to v5+. You must be on v4.0.60 or later.";
                LOGGER.error(MarkerFactory.getMarker("FATAL"), message);
                throw new RuntimeException(message);
            }
        }

        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(FLYWAY_LOCATIONS)
                .table(FLYWAY_TABLE)
                .baselineOnMigrate(true);

        if (baselineVersionAsString != null) {
            configuration = configuration.baselineVersion(baselineVersionAsString);
        }
        final Flyway flyway = configuration.load();
        if (baselineVersionAsString != null) {
            flyway.baseline();
        }
        migrateDatabase(flyway);
    }

    private void migrateDatabase(final Flyway flyway) {
        LOGGER.info("Applying Flyway migrations to {} in {} from {}", MODULE, FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating {} database", MODULE, e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for {} in {}", MODULE, FLYWAY_TABLE);
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected Class<CoreDbConnProvider> getConnectionProviderType() {
        return CoreDbConnProvider.class;
    }

    @Override
    protected CoreDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements CoreDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }

    private static class ForceMigration {
        @Inject
        ForceMigration(final CoreDbConnProvider provider) {
        }
    }
}
