package stroom.meta.impl.db;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.ExpressionMapper.TermHandler;
import stroom.dictionary.api.WordListProvider;
import stroom.meta.impl.MetaKeyDao;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.or;

class MetaExpressionMapper implements Function<ExpressionItem, Collection<Condition>> {
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
    public Collection<Condition> apply(final ExpressionItem item) {
        Collection<Condition> result = Collections.emptyList();

        if (item != null && item.isEnabled()) {
            if (item instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) item;
                final MetaTermHandler termHandler = termHandlers.get(term.getField());
                if (termHandler != null) {
                    result = Collections.singleton(termHandler.apply(term));
                }

            } else if (item instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) item;

                final Collection<Condition> children = operator.getChildren()
                        .stream()
                        .map(this)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

                if (children.size() > 0) {
                    switch (operator.getOp()) {
                        case AND:
                            result = children;
                            break;
                        case OR:
                            if (children.size() == 1) {
                                result = children;
                            } else {
                                result = Collections.singleton(or(children));
                            }
                            break;
                        case NOT:
                            result = children
                                    .stream()
                                    .map(DSL::not)
                                    .collect(Collectors.toList());
                    }
                }
            }
        }

        return result;
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
