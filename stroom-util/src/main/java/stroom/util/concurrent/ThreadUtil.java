package stroom.util.concurrent;

public class ThreadUtil {

    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }

    public static void sleep(final long millis, final int nanos) {
        try {
            Thread.sleep(millis, nanos);
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }
}
