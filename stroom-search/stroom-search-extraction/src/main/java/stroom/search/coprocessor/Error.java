package stroom.search.coprocessor;

public class Error {
    private final String message;
    private final Throwable throwable;

    public Error(final String message,
                 final Throwable throwable) {
        this.message = message;
        this.throwable = throwable;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public String toString() {
        return message;
    }
}
