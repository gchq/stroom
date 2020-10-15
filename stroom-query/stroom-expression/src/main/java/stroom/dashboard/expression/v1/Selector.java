package stroom.dashboard.expression.v1;

public abstract class Selector extends AbstractSingleChildGenerator {
    private static final long serialVersionUID = 8153777070911899616L;

    Selector(final Generator childGenerator) {
        super(childGenerator);
    }

    @Override
    public void set(final Val[] values) {
        childGenerator.set(values);
    }

    @Override
    public Val eval() {
        return childGenerator.eval();
    }

    public abstract Val select(Generator[] subGenerators);
}