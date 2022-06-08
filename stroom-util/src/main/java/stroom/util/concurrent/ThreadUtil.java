package stroom.util.concurrent;

public class ThreadUtil {

    public static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.reset(e);
        }
    }

    public static void sleep(final long millis, final int nanos) {
        try {
            Thread.sleep(millis, nanos);
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.reset(e);
        }
    }
}
