package stroom.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.ArrayDeque;
import java.util.Queue;

@Singleton
public class EntityManagerSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityManagerSupport.class);
    private final Provider<EntityManager> entityManagerProvider;
    private final ThreadLocal<Queue<Context>> threadLocal = new InheritableThreadLocal<>();

    @Inject
    public EntityManagerSupport(final Provider<EntityManager> entityManagerProvider) {
        this.entityManagerProvider = entityManagerProvider;
    }

    public void execute(final TransactionalRunnable runnable) {
        final Context currentContext = getContext();
        final Context context = pushContext(currentContext);
        final EntityManager entityManager = context.entityManager;

        try {
            runnable.run(entityManager);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            popContext();
        }
    }

    public <T> T executeResult(final TransactionalCallable<T> callable) {
        T t;

        final Context currentContext = getContext();
        final Context context = pushContext(currentContext);
        final EntityManager entityManager = context.entityManager;

        try {
            t = callable.run(entityManager);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            popContext();
        }

        return t;
    }

    public void transaction(final TransactionalRunnable runnable) {
        final Context currentContext = getContext();
        final Context context = pushContext(currentContext);
        final EntityManager entityManager = context.entityManager;

        if (!entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().begin();
        }

        try {
            runnable.run(entityManager);

            // Commit the current transaction if we are not nested.
            if (currentContext == null && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().commit();
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            entityManager.getTransaction().rollback();
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            entityManager.getTransaction().rollback();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            popContext();
        }
    }

    public <T> T transactionResult(final TransactionalCallable<T> callable) {
        T t;

        final Context currentContext = getContext();
        final Context context = pushContext(currentContext);
        final EntityManager entityManager = context.entityManager;

        if (!entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().begin();
        }

        try {
            t = callable.run(entityManager);

            // Commit the current transaction if we are not nested.
            if (currentContext == null && entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().commit();
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            entityManager.getTransaction().rollback();
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            entityManager.getTransaction().rollback();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            popContext();
        }

        return t;
    }

    private Context getContext() {
        Queue<Context> queue = threadLocal.get();
        if (queue == null) {
            return null;
        }
        return queue.peek();
    }

    private Context pushContext(final Context currentContext) {
        final Queue<Context> queue = getQueue();
        Context context;
        if (currentContext != null) {
            context = new Context(currentContext.entityManager);
        } else {
            context = new Context(entityManagerProvider.get());
        }
        queue.offer(context);
        return context;
    }

    private Queue<Context> getQueue() {
        Queue<Context> queue = threadLocal.get();
        if (queue == null) {
            queue = new ArrayDeque<>();
            threadLocal.set(queue);
        }
        return queue;
    }

    private void popContext() {
        final Queue<Context> queue = threadLocal.get();
        final Context context = queue.peek();
        if (queue.size() == 1) {
            threadLocal.set(null);
            context.entityManager.close();
        } else {
            queue.remove();
        }
    }

    private static class Context {
        private final EntityManager entityManager;

        Context(final EntityManager entityManager) {
            this.entityManager = entityManager;
        }
    }
}
