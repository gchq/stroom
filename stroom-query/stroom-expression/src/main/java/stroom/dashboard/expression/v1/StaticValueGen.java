package stroom.dashboard.expression.v1;

import stroom.dashboard.expression.v1.ref.StoredValues;

import java.util.Objects;
import java.util.function.Supplier;

public class StaticValueGen extends AbstractNoChildGenerator {

    private final Val value;

    StaticValueGen(final Val value) {
        this.value = value;
    }

    @Override
    public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
        return value;
    }

    public Val getValue() {
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
        final StaticValueGen that = (StaticValueGen) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
