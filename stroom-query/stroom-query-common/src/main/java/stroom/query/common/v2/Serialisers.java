package stroom.query.common.v2;

import javax.inject.Inject;

public class Serialisers {

    private final InputFactory inputFactory;
    private final OutputFactory outputFactory;

    @Inject
    public Serialisers(final ResultStoreConfig resultStoreConfig) {
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
