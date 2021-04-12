package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.dictionary.api.WordListProvider;
import stroom.docrefinfo.api.DocRefInfoService;

import org.jooq.Field;

import javax.inject.Inject;

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

    public <T> TermHandler<T> create(final AbstractField dataSourceField,
                                     final Field<T> field,
                                     final ExpressionMapper.MultiConverter<T> converter) {
        return create(dataSourceField, field, converter, false);
    }

    public <T> TermHandler<T> create(final AbstractField dataSourceField,
                                     final Field<T> field,
                                     final ExpressionMapper.MultiConverter<T> converter,
                                     final boolean useName) {
        return new TermHandler<T>(dataSourceField,
                field,
                converter, wordListProvider, collectionService, docRefInfoService, useName);
    }
}
