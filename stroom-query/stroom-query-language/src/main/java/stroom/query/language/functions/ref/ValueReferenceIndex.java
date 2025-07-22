package stroom.query.language.functions.ref;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ValueReferenceIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueReferenceIndex.class);

    private final List<ValueReference<?>> list = new ArrayList<>();

    public CountReference addCount(final String name) {
        return add(new CountReference(list.size(), name));
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
        final int index = list.indexOf(valueReference);
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

    public Integer getFieldValIndex(final String name) {
        for (int index = 0; index < list.size(); index++) {
            final ValueReference<?> valueReference = list.get(index);
            if (valueReference instanceof FieldValReference) {
                if (Objects.equals(valueReference.toString(), name)) {
                    return index;
                }
            }
        }
        return null;
    }

    private <T extends ValueReference<?>> T add(final T valueReference) {
        list.add(valueReference);
        return valueReference;
    }

    public StoredValues createStoredValues() {
        return new StoredValues(new Object[list.size()]);
    }

    public StoredValues read(final DataReader reader) {
        try {
            final StoredValues storedValues = createStoredValues();
            for (final ValueReference<?> valueReference : list) {
                valueReference.read(storedValues, reader);
            }
            return storedValues;
        } catch (final RuntimeException e) {
            final String sb = "Error reading value:\n" +
                              e.getClass().getSimpleName() +
                              "\n" +
                              e.getMessage() +
                              "\n" +
                              "Byte Buffer:\n" +
                              reader.toString() +
                              "\n" +
                              "Value Reference Index:\n" +
                              this;
            LOGGER.error(sb, e);

            throw e;
        }
    }

    public void write(final StoredValues storedValues, final DataWriter writer) {
        try {
            for (final ValueReference<?> valueReference : list) {
                valueReference.write(storedValues, writer);
            }
        } catch (final RuntimeException e) {
            final String sb = "Error writing value:\n" +
                              e.getClass().getSimpleName() +
                              "\n" +
                              e.getMessage() +
                              "\n" +
                              "Value Reference Index:\n" +
                              this;
            LOGGER.error(sb, e);

            throw e;
        }
    }

    @Override
    public String toString() {
        return list.toString();
    }
}
