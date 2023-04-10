package stroom.dashboard.expression.v1;

import java.util.function.Supplier;

abstract class Selector extends AbstractSingleChildGenerator {

    Selector(final Generator childGenerator) {
        super(childGenerator);
    }

    @Override
    public void set(final Val[] values) {
        childGenerator.set(values);
    }

    @Override
    public Val eval(final Supplier<ChildData> childDataSupplier) {
        Val val = null;
        if (childDataSupplier != null) {
            final ChildData childData = childDataSupplier.get();
            if (childData != null) {
                val = select(childData);
            }
        }

        if (val == null) {
            val = childGenerator.eval(childDataSupplier);
        }

        return val;
    }

    Val select(ChildData childData) {
        return null;
    }
}
