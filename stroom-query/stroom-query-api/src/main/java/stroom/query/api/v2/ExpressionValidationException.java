package stroom.query.api.v2;

public class ExpressionValidationException extends RuntimeException {

    public ExpressionValidationException(final String message) {
        super(message);
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
