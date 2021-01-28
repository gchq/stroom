package stroom.dashboard.expression.v1;

public enum ArgType {
    STRING("String"),
    DATE("Date"),
    NUMBER("Number"),
    BOOLEAN("Boolean");

    private final String name;

    ArgType(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
