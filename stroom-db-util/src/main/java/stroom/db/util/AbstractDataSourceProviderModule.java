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
import stroom.util.guice.GuiceUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * @param <T_CONFIG>    A config class that extends {@link AbstractDbConfig}
 * @param <T_CONN_PROV> A class that extends {@link DataSource}
 */
public abstract class AbstractDataSourceProviderModule<
        T_CONFIG extends AbstractDbConfig, T_CONN_PROV extends DataSource> extends AbstractModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDataSourceProviderModule.class);

    protected abstract String getModuleName();

    protected abstract Class<T_CONN_PROV> getConnectionProviderType();

    protected abstract T_CONN_PROV createConnectionProvider(DataSource dataSource);

    @Override
    protected void configure() {
        super.configure();

        LOGGER.debug("Configure() called on " + this.getClass().getCanonicalName());

        // MultiBind the connection provider so we can see status for all databases.
        NullSafe.consume(getConnectionProviderType(), type ->
                GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                        .addBinding(getConnectionProviderType()));
    }

    /**
     * We inject {@link ForceLegacyMigration} to ensure that the core DB migration has happened before all
     * other migrations.
     * <p>
     * This provider means the FlyWay migration will be triggered on first use of a datasource.
     */
    @Provides
    @Singleton
    public T_CONN_PROV getConnectionProvider(
            final Provider<T_CONFIG> configProvider,
            final DataSourceFactory dataSourceFactory,
            final Injector injector) {

        LOGGER.debug(() -> "Getting connection provider for " + getModuleName());

        final DataSource dataSource = dataSourceFactory.create(
                configProvider.get(),
                getModuleName(),
                createUniquePool());

        final DurationTimer timer = DurationTimer.start();
        final boolean didMigration = performMigration(dataSource, injector);
        if (didMigration) {
            LOGGER.info("Performed migration for {} in {}", getModuleName(), timer);
        }

        return createConnectionProvider(dataSource);
    }

    protected boolean createUniquePool() {
        return false;
    }

    /**
     * @param injector Don't inject classes from other db modules, or it will create unwanted
     *                 dependencies between modules. Intended for use with CrossModuleDbMigrationsModule
     *                 only.
     * @return True if the migration was run, whether it migrated anything or not.
     */
    protected abstract boolean performMigration(DataSource dataSource, final Injector injector);

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
