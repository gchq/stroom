package stroom.expression.matcher;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.dictionary.api.WordListProvider;

import javax.inject.Inject;
import java.util.Map;

public class ExpressionMatcherFactory {
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    @Inject
    ExpressionMatcherFactory(final WordListProvider wordListProvider, final CollectionService collectionService) {
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
    }

    public ExpressionMatcher create(final Map<String, AbstractField> fieldMap) {
        return new ExpressionMatcher(fieldMap, wordListProvider, collectionService);
    }
}