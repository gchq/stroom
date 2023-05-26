package stroom.query.common.v2;

public class Serialisers {
    private final OutputFactoryImpl outputFactory;

    public Serialisers(final AbstractResultStoreConfig resultStoreConfig) {
        this.outputFactory = new OutputFactoryImpl(resultStoreConfig);
    }

    public OutputFactoryImpl getOutputFactory() {
        return outputFactory;
    }
}
