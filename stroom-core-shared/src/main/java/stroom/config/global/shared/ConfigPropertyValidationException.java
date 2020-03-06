package stroom.config.global.shared;

public class ConfigPropertyValidationException  extends RuntimeException {

    public ConfigPropertyValidationException(String message) {
        super(message);
    }

    public ConfigPropertyValidationException(final String message,
                                             final Throwable cause) {
        super(message, cause);
    }

}
