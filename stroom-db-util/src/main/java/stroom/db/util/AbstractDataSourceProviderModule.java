package stroom.db.util;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.config.common.HasDbConfig;
import stroom.util.guice.GuiceUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @param <T_Config>       A config class that implements {@link HasDbConfig}
 * @param <T_ConnProvider> A class that extends {@link DataSource}
 */
public abstract class AbstractDataSourceProviderModule<T_Config extends HasDbConfig, T_ConnProvider extends DataSource> extends AbstractModule {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDataSourceProviderModule.class);

    protected abstract String getModuleName();

    protected abstract Class<T_ConnProvider> getConnectionProviderType();

    protected abstract T_ConnProvider createConnectionProvider(DataSource dataSource);

    private static final Set<String> COMPLETED_MIGRATIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    protected void configure() {
        super.configure();
        
        LOGGER.debug("Configure() called on " + this.getClass().getCanonicalName());

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class).addBinding(getConnectionProviderType());
    }

    @Provides
    @Singleton
    public T_ConnProvider getConnectionProvider(final Provider<T_Config> configProvider,
                                                final DataSourceFactory dataSourceFactory) {
        LOGGER.debug(() -> "Getting connection provider for " + getModuleName());

        final DataSource dataSource = dataSourceFactory.create(configProvider.get());

        // Prevent migrations from being re-run for each test
        if (!COMPLETED_MIGRATIONS.contains(getModuleName())) {
            performMigration(dataSource);
            COMPLETED_MIGRATIONS.add(getModuleName());
        }

        return createConnectionProvider(dataSource);
    }

    protected abstract void performMigration(final DataSource dataSource);

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
