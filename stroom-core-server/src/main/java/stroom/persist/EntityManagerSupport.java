package stroom.persist;

public interface EntityManagerSupport {
    void execute(EntityManagerRunnable runnable);

    <T> T executeResult(EntityManagerCallable<T> callable);

    void transaction(EntityManagerRunnable runnable);

    <T> T transactionResult(EntityManagerCallable<T> callable);
}
