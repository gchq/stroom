package stroom.util.exception;

@FunctionalInterface
public interface InterruptibleRunnable {

    void run() throws InterruptedException;

    /**
     * Wraps a runnable that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link RuntimeException},
     * thus making it unchecked and usable in a lambda.
     */
    static Runnable unchecked(InterruptibleRunnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted: " + e.getMessage(), e);
            }
        };
    }
}
