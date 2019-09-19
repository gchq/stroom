package stroom.db.util;

import stroom.streamstore.server.CollectionService;
import stroom.streamstore.server.WordListProvider;

import javax.inject.Inject;

public class ExpressionMapperFactory {
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    @Inject
    public ExpressionMapperFactory(final WordListProvider wordListProvider,
                                   final CollectionService collectionService) {
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
    }

    public ExpressionMapper create() {
        return new ExpressionMapper(wordListProvider, collectionService);
    }
}
