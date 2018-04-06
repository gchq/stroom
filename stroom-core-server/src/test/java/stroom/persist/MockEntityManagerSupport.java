package stroom.persist;

import javax.inject.Singleton;

@Singleton
public class MockEntityManagerSupport implements EntityManagerSupport {
    @Override
    public void execute(final EntityManagerRunnable runnable) {
        runnable.run(null);
    }

    @Override
    public <T> T executeResult(final EntityManagerCallable<T> callable) {
        return callable.run(null);
    }

    @Override
    public void transaction(final EntityManagerRunnable runnable) {
        runnable.run(null);
    }

    @Override
    public <T> T transactionResult(final EntityManagerCallable<T> callable) {
        return callable.run(null);
    }
}
