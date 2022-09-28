package stroom.util.exception;

@FunctionalInterface
public interface ThrowingRunnable<E extends Throwable> {

    void run() throws E;

    /**
     * Wraps a runnable that throws a checked exception with a catch block that will wrap
     * any thrown exception with a {@link RuntimeException}, thus making it unchecked and
     * usable in a lambda.
     */
    static <E extends Throwable> Runnable unchecked(ThrowingRunnable<E> runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
    }
}
