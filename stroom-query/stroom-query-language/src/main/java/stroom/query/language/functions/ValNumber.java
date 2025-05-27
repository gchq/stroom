package stroom.query.language.functions;

public sealed interface ValNumber
        extends Val
        permits ValByte, ValShort, ValInteger, ValLong, ValFloat, ValDouble, ValDate, ValDuration  {

    @Override
    default boolean hasNumericValue() {
        return true;
    }
}
