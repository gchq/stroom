package stroom.expression.matcher;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.QueryField;
import stroom.dictionary.api.WordListProvider;
import stroom.expression.api.DateTimeSettings;

import jakarta.inject.Inject;

import java.util.Map;

public class ExpressionMatcherFactory {

    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    @Inject
    ExpressionMatcherFactory(final WordListProvider wordListProvider, final CollectionService collectionService) {
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
    }

    public ExpressionMatcher create(final Map<String, QueryField> fieldMap) {
        return new ExpressionMatcher(fieldMap,
                wordListProvider,
                collectionService,
                DateTimeSettings.builder().build());
    }
}
