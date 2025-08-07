package stroom.planb.impl.data;

import java.util.Objects;

public class TraceAttribute {

    private final String name;
    private final String value;

    public TraceAttribute(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceAttribute that = (TraceAttribute) o;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return "TraceAttribute{" +
               "name='" + name + '\'' +
               ", value='" + value + '\'' +
               '}';
    }
}
