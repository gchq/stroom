package stroom.dashboard.expression.v1;

import stroom.dashboard.expression.v1.ref.StoredValues;

import java.util.function.Supplier;

abstract class Selector extends AbstractSingleChildGenerator {

    Selector(final Generator childGenerator) {
        super(childGenerator);
    }

    @Override
    public void set(final Val[] values, final StoredValues storedValues) {
        childGenerator.set(values, storedValues);
    }

    @Override
    public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
        Val val = null;
        if (childDataSupplier != null) {
            final ChildData childData = childDataSupplier.get();
            if (childData != null) {
                val = select(childGenerator, childData);
            }
        }

        if (val == null) {
            val = childGenerator.eval(storedValues, childDataSupplier);
        }

        return val;
    }

    Val select(Generator childGenerator, ChildData childData) {
        return null;
    }
}
