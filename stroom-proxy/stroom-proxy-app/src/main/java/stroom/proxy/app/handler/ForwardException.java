package stroom.proxy.app.handler;

import stroom.proxy.StroomStatusCode;

import java.util.Objects;

public class ForwardException extends Exception {

    private final StroomStatusCode stroomStatusCode;
    private final boolean isRecoverable;

    private ForwardException(final StroomStatusCode stroomStatusCode,
                             final String message,
                             final boolean isRecoverable,
                             final Throwable cause) {
        super(message, cause);
        this.isRecoverable = isRecoverable;
        this.stroomStatusCode = stroomStatusCode;
    }

    public static ForwardException recoverable(final StroomStatusCode stroomStatusCode,
                                               final String message,
                                               final Throwable cause) {
        return new ForwardException(stroomStatusCode, message, true, cause);
    }

    public static ForwardException recoverable(final StroomStatusCode stroomStatusCode) {
        Objects.requireNonNull(stroomStatusCode);
        return new ForwardException(stroomStatusCode, stroomStatusCode.getMessage(), true, null);
    }

    public static ForwardException nonRecoverable(final StroomStatusCode stroomStatusCode,
                                                  final String message,
                                                  final Throwable cause) {
        return new ForwardException(stroomStatusCode, message, false, cause);
    }

    public static ForwardException nonRecoverable(final StroomStatusCode stroomStatusCode) {
        Objects.requireNonNull(stroomStatusCode);
        return new ForwardException(stroomStatusCode, stroomStatusCode.getMessage(), false, null);
    }

    public boolean isRecoverable() {
        return isRecoverable;
    }
}
