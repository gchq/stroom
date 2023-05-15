package stroom.dashboard.expression.v1.ref;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ValueReferenceIndex {

    private final List<ValueReference<?>> list = new ArrayList<>();

    public CountReference addCount() {
        return add(new CountReference(list.size()));
    }

    public CountIterationReference addCountIteration(final int iteration) {
        return add(new CountIterationReference(list.size(), iteration));
    }

    public DoubleListReference addDoubleList() {
        return add(new DoubleListReference(list.size()));
    }

    public StringListReference addStringList() {
        return add(new StringListReference(list.size()));
    }

    public ValListReference addValList() {
        return add(new ValListReference(list.size()));
    }

    public FieldValReference addFieldValue(final int fieldIndex) {
        final FieldValReference valueReference =
                new FieldValReference(list.size(), fieldIndex);

        // Only store one reference to each field.
        int index = list.indexOf(valueReference);
        if (index != -1) {
            return (FieldValReference) list.get(index);
        }

        list.add(valueReference);
        return valueReference;
    }

    public RandomValReference addRandomValue() {
        return add(new RandomValReference(list.size()));
    }

    public ValReference addValue() {
        return add(new ValReference(list.size()));
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
