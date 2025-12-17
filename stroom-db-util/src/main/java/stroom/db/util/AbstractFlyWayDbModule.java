/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.db.util;

import stroom.config.common.AbstractDbConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    protected boolean performMigration(final DataSource dataSource, final Injector injector) {
        FlywayUtil.migrate(dataSource,
                getFlyWayLocations(),
                getMigrationTarget().orElse(null),
                getFlyWayTableName(),
                getModuleName());
        return true;
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
                .toList();
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
                .toList();
    }
}
