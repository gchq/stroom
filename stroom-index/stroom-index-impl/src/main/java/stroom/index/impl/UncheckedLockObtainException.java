package stroom.index.impl;

import java.io.IOException;

public class UncheckedLockObtainException extends RuntimeException {

    public UncheckedLockObtainException(final IOException e) {
        super(e);
    }
}
