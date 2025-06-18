package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;

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

    Val select(final Generator childGenerator, final ChildData childData) {
        return null;
    }
}
