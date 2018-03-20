package stroom.util.test.data;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Class to hold the definition of a field in a set of flat test data records.
 * Multiple static factory methods exist for creating various pre-canned types
 * of test data field, e.g. a random IP address
 */
public class Field {

    private static final Logger LOGGER = LoggerFactory.getLogger(Field.class);

    private final String name;
    private final Supplier<String> valueFunction;

    /**
     * @param name          The name of the field
     * @param valueSupplier A supplier of values for the field
     */
    public Field(final String name,
                 final Supplier<String> valueSupplier) {

        this.name = Preconditions.checkNotNull(name);
        this.valueFunction = Preconditions.checkNotNull(valueSupplier);
    }

    /**
     * @return The next value for this field from the value supplier.
     * The value supplier may either be stateful, i.e. the next value is
     * dependant on values that cam before it or stateless, i.e. the next
     * value has no relation to previous values.
     */
    public String getNext() {
        return valueFunction.get();
    }

    /**
     * @return The name of the field
     */
    public String getName() {
        return name;
    }

}
