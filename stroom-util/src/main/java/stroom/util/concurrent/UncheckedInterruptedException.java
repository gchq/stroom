package stroom.util.concurrent;

public class UncheckedInterruptedException extends RuntimeException {

    public UncheckedInterruptedException(final String message, final InterruptedException interruptedException) {
        super(message, interruptedException);
    }

    public UncheckedInterruptedException(final InterruptedException interruptedException) {
        super(interruptedException.getMessage(), interruptedException);
    }
}
