package stroom.query.language.functions;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum Type implements HasPrimitiveValue {
    NULL("null", 0, false, false, false, true),
    BOOLEAN("boolean", 1, true, false, false, false),
    FLOAT("float", 2, true, true, false, false),
    DOUBLE("double", 3, true, true, false, false),
    INTEGER("integer", 4, true, true, false, false),
    LONG("long", 5, true, true, false, false),
    DATE("date", 6, true, true, false, false),
    STRING("string", 7, true, false, false, false),
    ERR("error", 9, false, false, true, false),
    DURATION("duration", 10, true, true, false, false),
    BYTE("byte", 11, true, true, false, false),
    SHORT("short", 12, true, true, false, false),
    XML("xml", 13, true, false, false, false);

    private final String name;
    private final byte id;
    private final boolean isValue;
    private final boolean isNumber;
    private final boolean isError;
    private final boolean isNull;

    public static final PrimitiveValueConverter<Type> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(Type.class, Type.values());

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

    @Override
    public byte getPrimitiveValue() {
        return id;
    }
}
