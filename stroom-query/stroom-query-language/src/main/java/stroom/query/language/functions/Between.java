package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.Arrays;
import java.util.function.Supplier;

public class Between extends AbstractFunction {

    public Between() {
        super("between", 3, 3);
    }

    public Between(final String name) {
        super(name, 3, 3);
    }

    @Override
    public void addValueReferences(ValueReferenceIndex valueReferenceIndex) {
        valueReferenceIndex.addValue("value");
        valueReferenceIndex.addValue("lower");
        valueReferenceIndex.addValue("upper");
    }

    @Override
    public Generator createGenerator() {
        return new Generator() {

            @Override
            public void set(final Val[] values, final StoredValues storedValues) {
//                if (values != null && storedValues != null && values.length == 3) {
//                    storedValues.set(0, values[0]);
//                    storedValues.set(1, values[1]);
//                    storedValues.set(2, values[2]);
//                }
            }

            @Override
            public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
                final Param[] params = Between.this.params;
                if (params == null || params.length != 3) {
                    return ValErr.create("Between function requires exactly 3 parameters");
                }

                final Val value = params[0] instanceof Val ? (Val) params[0] : Val.create(params[0]);
                final Val lower = params[1] instanceof Val ? (Val) params[1] : Val.create(params[1]);
                final Val upper = params[2] instanceof Val ? (Val) params[2] : Val.create(params[2]);

                if (value == null || lower == null || upper == null
                    || value == ValNull.INSTANCE
                    || lower == ValNull.INSTANCE
                    || upper == ValNull.INSTANCE) {
                    return ValErr.create("Null parameter in between function");
                }

                // Check if all are date strings
                if (value instanceof ValString && lower instanceof ValString && upper instanceof ValString) {
                    try {
                        final java.time.LocalDate v = java.time.LocalDate.parse(value.toString());
                        final java.time.LocalDate l = java.time.LocalDate.parse(lower.toString());
                        final java.time.LocalDate u = java.time.LocalDate.parse(upper.toString());
                        return ValBoolean.create((!v.isBefore(l)) && (!v.isAfter(u)));
                    } catch (Exception e) {
                        return ValErr.create("Parameters must be valid ISO date strings for between function");
                    }
                }

                // Check if all are numeric
                if (value instanceof ValNumber && lower instanceof ValNumber && upper instanceof ValNumber) {
                    try {
                        final double v = value.toDouble();
                        final double l = lower.toDouble();
                        final double u = upper.toDouble();
                        return ValBoolean.create(l <= v && v <= u);
                    } catch (Exception e) {
                        return ValErr
                                .create("Parameters must be all numeric or all ISO date strings for between function");
                    }
                }

                return ValErr.create("Parameters must be all numeric or all ISO date strings for between function");
            }

            @Override
            public void merge(final StoredValues existingValues, final StoredValues newValues) {
            }
        };
    }

    public boolean hasAggregate() {
        return false;
    }
}
