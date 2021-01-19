package stroom.dashboard.expression.v1;

public enum FunctionCategory {
    AGGREGATE("Aggregate"),
    CAST("Cast"),
    DATE("Date"),
    LINK("Link"),
    LOGIC("Logic"),
    MATHEMATICS("Mathematics"),
    PARAM("Param"),
    SELECTION("Selection"),
    STRING("String"),
    TYPE_CHECKING("Type Checking"),
    URI("URI"),
    VALUE("Value");

    private final String name;

    FunctionCategory(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
