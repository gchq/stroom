package stroom.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

public class PersistenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceManager.class);

    private final Provider<EntityManagerFactory> entityManagerFactoryProvider;
    private final Provider<DataSource> dataSourceProvider;

    @Inject
    PersistenceManager(final Provider<EntityManagerFactory> entityManagerFactoryProvider,
                       final Provider<DataSource> dataSourceProvider) {
        this.entityManagerFactoryProvider = entityManagerFactoryProvider;
        this.dataSourceProvider = dataSourceProvider;
    }

    public void shutdown() {
        // Shutdown persistence.
        try {
            entityManagerFactoryProvider.get().close();
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

//        try {
//            dataSourceProvider.get().getConnection().close();
//        } catch (final Exception e) {
//            LOGGER.error(e.getMessage(), e);
//        }
    }
}
