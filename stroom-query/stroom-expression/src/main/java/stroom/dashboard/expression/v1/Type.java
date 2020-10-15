package stroom.dashboard.expression.v1;

interface Type {
    boolean isValue();

    boolean isNumber();

    boolean isError();

    boolean isNull();
}
