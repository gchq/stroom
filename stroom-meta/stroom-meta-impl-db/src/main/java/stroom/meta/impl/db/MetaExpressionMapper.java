package stroom.meta.impl.db;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.ExpressionMapper.TermHandler;
import stroom.dictionary.api.WordListProvider;
import stroom.meta.impl.MetaKeyDao;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.or;

class MetaExpressionMapper implements Function<ExpressionItem, Condition> {
    private final MetaKeyDao metaKeyDao;
    private final Field<Integer> keyField;
    private final Field<Long> valueField;
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    private final Map<String, MetaTermHandler> termHandlers = new HashMap<>();

    MetaExpressionMapper(final MetaKeyDao metaKeyDao,
                         final Field<Integer> keyField,
                         final Field<Long> valueField,
                         final WordListProvider wordListProvider,
                         final CollectionService collectionService) {
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

        termHandlers.put(dataSourceField.getName(), handler);
    }

    @Override
    public Condition apply(final ExpressionItem item) {
        if (item == null || !item.isEnabled()) {
            return null;
        }

        if (item instanceof ExpressionTerm) {
            final ExpressionTerm term = (ExpressionTerm) item;
            final MetaTermHandler termHandler = termHandlers.get(term.getField());
            if (termHandler == null) {
//                throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
                return null;
            }

            return termHandler.apply(term);

        } else if (item instanceof ExpressionOperator) {
            final ExpressionOperator operator = (ExpressionOperator) item;

            final Condition[] children = operator.getChildren().stream()
                    .map(this)
                    .filter(Objects::nonNull)
                    .toArray(Condition[]::new);

            if (children.length > 0) {
                switch (operator.getOp()) {
                    case AND:
                        return and(children);
                    case OR:
                        return or(children);
                    case NOT:

                        if (children.length == 1) {
                            // A single child, just apply the 'not' to that first item
                            return not(children[0]);
                        } else {
                            // If there are multiple children, apply an 'and' around them all
                            return and(Arrays.stream(children)
                                    .map(DSL::not)
                                    .toArray(Condition[]::new));
                        }
                    default:
                        // Fall through to null if there aren't any children
                        break;
                }
            }
        }

        return null;
    }

    static class MetaTermHandler {
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

        Condition apply(final ExpressionTerm term) {
            return metaKeyDao.getIdForName(key)
                    .map(id -> keyField.equal(id).and(valueHandler.apply(term)))
                    .orElse(null);
        }
    }
}
