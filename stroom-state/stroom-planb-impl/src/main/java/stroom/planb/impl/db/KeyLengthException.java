package stroom.planb.impl.db;

public class KeyLengthException extends RuntimeException {

    public KeyLengthException(final int max) {
        super("Key length exceeds " + max + " bytes");
    }
}
