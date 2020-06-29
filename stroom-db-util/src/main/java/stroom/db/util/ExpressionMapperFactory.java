package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionItem;

import org.jooq.Condition;

import javax.inject.Inject;
import java.util.function.Function;

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
        return new ExpressionMapper(wordListProvider, collectionService, null);
    }

    public ExpressionMapper create(final Function<ExpressionItem, Condition> delegateItemHandler) {
        return new ExpressionMapper(wordListProvider, collectionService, delegateItemHandler);
    }
}
