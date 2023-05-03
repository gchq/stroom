package stroom.query.common.v2;

public class Serialisers {

    private final InputFactory inputFactory;
    private final OutputFactory outputFactory;

    public Serialisers(final AbstractResultStoreConfig resultStoreConfig) {
        this.inputFactory = new InputFactory();
        this.outputFactory = new OutputFactory(resultStoreConfig);
    }

    public InputFactory getInputFactory() {
        return inputFactory;
    }

    public OutputFactory getOutputFactory() {
        return outputFactory;
    }
}
