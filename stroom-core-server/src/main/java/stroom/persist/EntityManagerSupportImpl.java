package stroom.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;

@Singleton
public class EntityManagerSupportImpl implements EntityManagerSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityManagerSupportImpl.class);
    private final PersistServiceImpl persistService;

    @Inject
    public EntityManagerSupportImpl(final PersistServiceImpl persistService) {
        this.persistService = persistService;
    }

    @Override
    public void execute(final EntityManagerRunnable runnable) {
        persistService.begin();
        final EntityManager entityManager = persistService.get();

        try {
            runnable.run(entityManager);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            persistService.end();
        }
    }

    @Override
    public <T> T executeResult(final EntityManagerCallable<T> callable) {
        T t;

        persistService.begin();
        final EntityManager entityManager = persistService.get();

        try {
            t = callable.run(entityManager);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            persistService.end();
        }

        return t;
    }

    @Override
    public void transaction(final EntityManagerRunnable runnable) {
        persistService.begin();
        final EntityManager entityManager = persistService.get();

        boolean startedTransaction = false;
        if (!entityManager.getTransaction().isActive()) {
            startedTransaction = true;
            entityManager.getTransaction().begin();
        }

        try {
            runnable.run(entityManager);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            entityManager.getTransaction().setRollbackOnly();
            throw e;
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            entityManager.getTransaction().setRollbackOnly();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            try {
                // Commit the current transaction if we are not nested.
                if (startedTransaction) {
                    if (entityManager.getTransaction().getRollbackOnly()) {
                        entityManager.getTransaction().rollback();
                    } else {
                        entityManager.getTransaction().commit();
                    }
                }
            } finally {
                persistService.end();
            }
        }
    }

    @Override
    public <T> T transactionResult(final EntityManagerCallable<T> callable) {
        T t;

        persistService.begin();
        final EntityManager entityManager = persistService.get();

        boolean startedTransaction = false;
        if (!entityManager.getTransaction().isActive()) {
            startedTransaction = true;
            entityManager.getTransaction().begin();
        }

        try {
            t = callable.run(entityManager);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            entityManager.getTransaction().setRollbackOnly();
            throw e;
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            entityManager.getTransaction().setRollbackOnly();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            try {
                // Commit the current transaction if we are not nested.
                if (startedTransaction) {
                    if (entityManager.getTransaction().getRollbackOnly()) {
                        entityManager.getTransaction().rollback();
                    } else {
                        entityManager.getTransaction().commit();
                    }
                }
            } finally {
                persistService.end();
            }
        }

        return t;
    }
}
