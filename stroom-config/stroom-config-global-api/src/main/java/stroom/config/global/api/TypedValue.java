package stroom.config.global.api;

public class TypedValue<T> {

    private final T value;
    private final Class<T> type;

    public TypedValue(final T value, final Class<T> type) {
        this.value = value;
        this.type = type;
    }

    public T getValue() {
        return value;
    }

    public Class<T> getType() {
        return type;
    }
}
