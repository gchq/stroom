package stroom.dashboard.expression.v1;

public enum Type {
    NULL("null", 0, false, false, false, true),
    BOOLEAN("boolean", 1, true, false, false, false),
    DOUBLE("double", 2, true, true, false, false),
    INTEGER("integer", 3, true, true, false, false),
    LONG("long", 4, true, true, false, false),
    STRING("string", 5, true, false, false, false),
    ERR("error", 9, false, false, true, false);

    private final String name;
    private final byte id;
    private final boolean isValue;
    private final boolean isNumber;
    private final boolean isError;
    private final boolean isNull;

    Type(final String name,
         final int id,
         final boolean isValue,
         final boolean isNumber,
         final boolean isError,
         final boolean isNull) {
        this.name = name;
        this.id = (byte) id;
        this.isValue = isValue;
        this.isNumber = isNumber;
        this.isError = isError;
        this.isNull = isNull;
    }

    public String getName() {
        return name;
    }

    public byte getId() {
        return id;
    }

    public boolean isValue() {
        return isValue;
    }

    public boolean isNumber() {
        return isNumber;
    }

    public boolean isError() {
        return isError;
    }

    public boolean isNull() {
        return isNull;
    }

    @Override
    public String toString() {
        return name;
    }
}
