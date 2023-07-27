package stroom.dashboard.expression.v1.ref;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ValueReferenceIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueReferenceIndex.class);

    private final List<ValueReference<?>> list = new ArrayList<>();

    public CountReference addCount(final String name) {
        return add(new CountReference(list.size(), name));
    }

    public CountIterationReference addCountIteration(final String name, final int iteration) {
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

    public FieldValReference addFieldValue(final String name, final int fieldIndex) {
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
        final ByteBuffer copy = byteBuffer.duplicate();
        try {
            try (final MyByteBufferInput input = new MyByteBufferInput(byteBuffer)) {
                return read(input);
            }
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Error reading value:\n");
            sb.append(e.getClass().getSimpleName());
            sb.append("\n");
            sb.append(e.getMessage());
            sb.append("\n");
            sb.append("Byte Buffer:\n");

            final byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);

            sb.append(Arrays.toString(bytes));
            sb.append("\n");
            sb.append("Value Reference Index:\n");
            sb.append(this);
            LOGGER.error(sb.toString(), e);

            throw e;
        }
    }

    public void write(final StoredValues storedValues, final MyByteBufferOutput output) {
        try {
            for (ValueReference<?> valueReference : list) {
                valueReference.write(storedValues, output);
            }
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Error writing value:\n");
            sb.append(e.getClass().getSimpleName());
            sb.append("\n");
            sb.append(e.getMessage());
            sb.append("\n");
            sb.append("Value Reference Index:\n");
            sb.append(this);
            LOGGER.error(sb.toString(), e);

            throw e;
        }
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
