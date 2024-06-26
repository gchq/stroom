package stroom.query.language.functions.ref;

import java.util.Objects;

public class FieldValReference extends ValReference {

    private final int fieldIndex;

    FieldValReference(final int index,
                      final int fieldIndex,
                      final String name) {
        super(index, name);
        this.fieldIndex = fieldIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FieldValReference that = (FieldValReference) o;
        return fieldIndex == that.fieldIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldIndex);
    }
}
