package stroom.query.common.v2;

public class Serialisers {
    private final InputFactory inputFactory;
    private final OutputFactory outputFactory;

    public Serialisers(final ErrorConsumer errorConsumer) {
        this(new InputFactory(), new OutputFactory(new ResultStoreConfig(), errorConsumer));
    }

    public Serialisers(final InputFactory inputFactory,
                       final OutputFactory outputFactory) {
        this.inputFactory = inputFactory;
        this.outputFactory = outputFactory;
    }

    public InputFactory getInputFactory() {
        return inputFactory;
    }

    public OutputFactory getOutputFactory() {
        return outputFactory;
    }
}
