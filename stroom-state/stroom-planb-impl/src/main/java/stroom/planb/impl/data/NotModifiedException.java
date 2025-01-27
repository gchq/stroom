package stroom.planb.impl.data;

public class NotModifiedException extends RuntimeException {

    public NotModifiedException() {
        this("Client already has latest snapshot");
    }

    public NotModifiedException(final String message) {
        super(message);
    }
}
