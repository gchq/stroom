package stroom.process.impl.db;

import javax.inject.Inject;

//TODO temporary to get the connProvider and flyway initialised
public class ProcessorServiceImpl {

    private final ConnectionProvider connectionProvider;

    @Inject
    public ProcessorServiceImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }
}
