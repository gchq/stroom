package stroom.meta.impl.db;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.CommonExpressionMapper;
import stroom.db.util.CommonExpressionMapper.TermHandler;
import stroom.dictionary.api.WordListProvider;
import stroom.meta.impl.MetaKeyDao;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionTerm;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.List;
import java.util.function.Function;

class MetaExpressionMapper implements Function<ExpressionItem, Condition> {
    private final CommonExpressionMapper expressionMapper;
    private final MetaKeyDao metaKeyDao;
    private final Field<Integer> keyField;
    private final Field<Long> valueField;
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    MetaExpressionMapper(final MetaKeyDao metaKeyDao,
                         final Field<Integer> keyField,
                         final Field<Long> valueField,
                         final WordListProvider wordListProvider,
                         final CollectionService collectionService) {
        expressionMapper = new CommonExpressionMapper();
        this.metaKeyDao = metaKeyDao;
        this.keyField = keyField;
        this.valueField = valueField;
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
    }

    public void map(final AbstractField dataSourceField) {
        final TermHandler<Long> termHandler = new TermHandler<>(dataSourceField, valueField, value -> List.of(Long.valueOf(value)), wordListProvider, collectionService);

        final MetaTermHandler handler = new MetaTermHandler(
                metaKeyDao,
                keyField,
                dataSourceField.getName(),
                termHandler);

        expressionMapper.addHandler(dataSourceField, handler);
    }

    @Override
    public Condition apply(final ExpressionItem expressionItem) {
        return expressionMapper.apply(expressionItem);
    }

    static class MetaTermHandler implements Function<ExpressionTerm, Condition> {
        private final MetaKeyDao metaKeyDao;
        private final Field<Integer> keyField;
        private final String key;
        private final TermHandler<Long> valueHandler;

        MetaTermHandler(final MetaKeyDao metaKeyDao, final Field<Integer> keyField, final String key, final TermHandler<Long> valueHandler) {
            this.metaKeyDao = metaKeyDao;
            this.keyField = keyField;
            this.valueHandler = valueHandler;
            this.key = key;
        }

        @Override
        public Condition apply(final ExpressionTerm term) {
            return metaKeyDao.getIdForName(key)
                    .map(id -> keyField.equal(id).and(valueHandler.apply(term)))
                    .orElse(null);
        }
    }
}
