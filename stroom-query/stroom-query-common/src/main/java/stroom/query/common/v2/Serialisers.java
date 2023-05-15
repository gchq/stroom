package stroom.query.common.v2;

public class Serialisers {

    private final InputFactoryImpl inputFactory;
    private final OutputFactoryImpl outputFactory;

    public Serialisers(final AbstractResultStoreConfig resultStoreConfig) {
        this.inputFactory = new InputFactoryImpl();
        this.outputFactory = new OutputFactoryImpl(resultStoreConfig);
    }

    public InputFactoryImpl getInputFactory() {
        return inputFactory;
    }

    public OutputFactoryImpl getOutputFactory() {
        return outputFactory;
    }
}
