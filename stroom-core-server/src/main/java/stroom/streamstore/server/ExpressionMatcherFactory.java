package stroom.streamstore.server;

import org.springframework.stereotype.Component;
import stroom.datasource.api.v2.DataSourceField;
import stroom.dictionary.server.DictionaryStore;

import javax.inject.Inject;
import java.time.ZoneOffset;
import java.util.Map;

@Component
public class ExpressionMatcherFactory {
    private final DictionaryStore dictionaryStore;
    private final CollectionService collectionService;

    @Inject
    public ExpressionMatcherFactory(final DictionaryStore dictionaryStore, final CollectionService collectionService) {
        this.dictionaryStore = dictionaryStore;
        this.collectionService = collectionService;
    }

    public ExpressionMatcher create(final Map<String, DataSourceField> fieldMap) {
        return new ExpressionMatcher(fieldMap, dictionaryStore, collectionService, ZoneOffset.UTC.getId(), System.currentTimeMillis());
    }
}
