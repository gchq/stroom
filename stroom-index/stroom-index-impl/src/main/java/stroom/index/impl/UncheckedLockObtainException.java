package stroom.index.impl;

import java.io.IOException;

public class UncheckedLockObtainException extends RuntimeException {

    public UncheckedLockObtainException(IOException e) {
        super(e);
    }
}
