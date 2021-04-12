package stroom.meta.impl.db;

import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.CommonExpressionMapper;
import stroom.db.util.TermHandler;
import stroom.db.util.TermHandlerFactory;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.db.jooq.tables.MetaVal;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionTerm;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

class MetaExpressionMapper implements Function<ExpressionItem, Condition> {

    private final CommonExpressionMapper expressionMapper;
    private final MetaKeyDao metaKeyDao;
    private final String keyFieldName;
    private final String valueFieldName;
    private final String metaIdFieldName;
    private final TermHandlerFactory termHandlerFactory;

    MetaExpressionMapper(final MetaKeyDao metaKeyDao,
                         final String keyFieldName,
                         final String valueFieldName,
                         final String metaIdFieldName,
                         final TermHandlerFactory termHandlerFactory) {
        expressionMapper = new CommonExpressionMapper();
        this.metaKeyDao = metaKeyDao;
        this.keyFieldName = keyFieldName;
        this.metaIdFieldName = metaIdFieldName;
        this.valueFieldName = valueFieldName;
        this.termHandlerFactory = termHandlerFactory;
    }

    public void map(final AbstractField dataSourceField) {
        Optional<Integer> idOptional = metaKeyDao.getIdForName(dataSourceField.getName());

        if (idOptional.isPresent()) {
            int id = idOptional.get();
            Field<Long> valueField = createValueField(id);

            final TermHandler<Long> termHandler = termHandlerFactory.create(
                    dataSourceField,
                    valueField,
                    value -> List.of(Long.valueOf(value)));

            final MetaTermHandler handler = new MetaTermHandler(
                    createKeyField(id),
                    id,
                    termHandler);
            expressionMapper.addHandler(dataSourceField, handler);
        }
    }

    public SelectJoinStep<?> addJoins(
            SelectJoinStep<?> query,
            final Field<Long> metaIdField,
            final Set<Integer> usedValKeys) {

        for (Integer id : usedValKeys) {
            query = query.leftOuterJoin(MetaVal.META_VAL.as("v" + id))
                    .on(metaIdField.eq(createMetaIdField(id))); //Join on meta_val
        }
        return query;
    }

    private String createTableName(final int valKeyId) {
        return "`v" + valKeyId + "`";
    }

    private Field<Long> createValueField(final int valKeyId) {
        return DSL.field(createTableName(valKeyId) + ".`" + valueFieldName + "`", Long.class);
    }

    private Field<Integer> createKeyField(final int valKeyId) {
        return DSL.field(createTableName(valKeyId) + ".`" + keyFieldName + "`", Integer.class);
    }

    private Field<Long> createMetaIdField(final int valKeyId) {
        return DSL.field(createTableName(valKeyId) + ".`" + metaIdFieldName + "`", Long.class);
    }

    @Override
    public Condition apply(final ExpressionItem expressionItem) {
        return expressionMapper.apply(expressionItem);
    }

    static class MetaTermHandler implements Function<ExpressionTerm, Condition> {

        private final Field<Integer> keyField;
        private final Integer id;
        private final TermHandler<Long> valueHandler;

        MetaTermHandler(final Field<Integer> keyField, final Integer id, final TermHandler<Long> valueHandler) {
            this.keyField = keyField;
            this.valueHandler = valueHandler;
            this.id = id;
        }

        @Override
        public Condition apply(final ExpressionTerm term) {
            return keyField.equal(id).and(valueHandler.apply(term));
        }
    }
}
