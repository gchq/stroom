package stroom.db.util;

import stroom.config.common.AbstractDbConfig;
import stroom.util.db.ForceLegacyMigration;
import stroom.util.guice.GuiceUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.inject.Provider;
import javax.inject.Singleton;
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
        GuiceUtil.buildMultiBinder(binder(), DataSource.class).addBinding(getConnectionProviderType());
    }

    /**
     * We inject {@link ForceLegacyMigration} to ensure that the the core DB migration has happened before all
     * other migrations
     */
    @Provides
    @Singleton
    public T_CONN_PROV getConnectionProvider(
            final Provider<T_CONFIG> configProvider,
            final DataSourceFactory dataSourceFactory,
            @SuppressWarnings("unused") final ForceLegacyMigration forceLegacyMigration) {

        LOGGER.debug(() -> "Getting connection provider for " + getModuleName());

        final DataSource dataSource = dataSourceFactory.create(
                configProvider.get(),
                getModuleName(),
                createUniquePool());
        performMigration(dataSource);
        return createConnectionProvider(dataSource);
    }

    protected boolean createUniquePool() {
        return false;
    }

    protected abstract void performMigration(DataSource dataSource);

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
