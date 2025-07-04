package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.api.datasource.QueryField;

import com.google.inject.Provider;
import jakarta.inject.Inject;
import org.jooq.Field;

public class TermHandlerFactory {

    private final Provider<WordListProvider> wordListProvider;
    private final Provider<CollectionService> collectionService;
    private final Provider<DocRefInfoService> docRefInfoService;

    @Inject
    public TermHandlerFactory(final Provider<WordListProvider> wordListProvider,
                              final Provider<CollectionService> collectionService,
                              final Provider<DocRefInfoService> docRefInfoService) {
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
        this.docRefInfoService = docRefInfoService;
    }

    public <T> TermHandler<T> create(final QueryField dataSourceField,
                                     final Field<T> field,
                                     final ExpressionMapper.MultiConverter<T> converter) {
        return create(dataSourceField, field, converter, false);
    }

    public <T> TermHandler<T> create(final QueryField dataSourceField,
                                     final Field<T> field,
                                     final ExpressionMapper.MultiConverter<T> converter,
                                     final boolean useName) {
        return new TermHandler<>(dataSourceField,
                field,
                converter,
                wordListProvider,
                collectionService,
                docRefInfoService,
                useName,
                false);
    }

}
