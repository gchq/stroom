package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.api.datasource.QueryField;

import jakarta.inject.Inject;
import org.jooq.Field;

public class TermHandlerFactory {

    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;
    private final DocRefInfoService docRefInfoService;

    @Inject
    public TermHandlerFactory(final WordListProvider wordListProvider,
                              final CollectionService collectionService,
                              final DocRefInfoService docRefInfoService) {
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
        return new TermHandler<T>(dataSourceField,
                field,
                converter, wordListProvider, collectionService, docRefInfoService, useName, false);
    }
}
