package stroom.dashboard.expression.v1.ref;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ValueReferenceIndex {

    private final List<ValueReference<?>> list = new ArrayList<>();

    public CountReference addCount(final String name) {
        return add(new CountReference(list.size(), name));
    }

    public CountIterationReference addCountIteration(final int iteration, final String name) {
        return add(new CountIterationReference(list.size(), name, iteration));
    }

    public DoubleListReference addDoubleList(final String name) {
        return add(new DoubleListReference(list.size(), name));
    }

    public StringListReference addStringList(final String name) {
        return add(new StringListReference(list.size(), name));
    }

    public ValListReference addValList(final String name) {
        return add(new ValListReference(list.size(), name));
    }

    public FieldValReference addFieldValue(final int fieldIndex, final String name) {
        final FieldValReference valueReference =
                new FieldValReference(list.size(), fieldIndex, name);

        // Only store one reference to each field.
        int index = list.indexOf(valueReference);
        if (index != -1) {
            return (FieldValReference) list.get(index);
        }

        list.add(valueReference);
        return valueReference;
    }

    public RandomValReference addRandomValue(final String name) {
        return add(new RandomValReference(list.size(), name));
    }

    public ValReference addValue(final String name) {
        return add(new ValReference(list.size(), name));
    }

    private <T extends ValueReference<?>> T add(T valueReference) {
        list.add(valueReference);
        return valueReference;
    }

    public StoredValues createStoredValues() {
        return new StoredValues(new Object[list.size()]);
    }

    public StoredValues read(final MyByteBufferInput input) {
        final StoredValues storedValues = createStoredValues();
        for (ValueReference<?> valueReference : list) {
            valueReference.read(storedValues, input);
        }
        return storedValues;
    }

    public StoredValues read(final ByteBuffer byteBuffer) {
        try (final MyByteBufferInput input = new MyByteBufferInput(byteBuffer)) {
            return read(input);
        }
    }

    public void write(final StoredValues storedValues, final MyByteBufferOutput output) {
        for (ValueReference<?> valueReference : list) {
            valueReference.write(storedValues, output);
        }
    }
}
