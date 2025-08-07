package stroom.pathways.model.trace;

public enum StatusCode {
    STATUS_CODE_UNSET(0),
    STATUS_CODE_OK(1),
    STATUS_CODE_ERROR(2);

    private final int value;

    StatusCode(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
