package stroom.query.language.functions;

public sealed interface ValNumber
        extends Val
        permits ValDate, ValDouble, ValDuration, ValFloat, ValInteger, ValLong {

    @Override
    default boolean hasNumericValue() {
        return true;
    }
}
