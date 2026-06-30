/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.meta.impl.db;

import stroom.db.util.CommonExpressionMapper;
import stroom.db.util.TermHandler;
import stroom.db.util.TermHandlerFactory;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.db.jooq.tables.MetaVal;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SelectJoinStep;
import org.jooq.Table;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class MetaExpressionMapper implements Function<ExpressionItem, Condition> {

    private static final String META_ALIAS_PREFIX = "mv_";

    private final CommonExpressionMapper expressionMapper;
    private final MetaKeyDao metaKeyDao;
    private final TermHandlerFactory termHandlerFactory;

    MetaExpressionMapper(final MetaKeyDao metaKeyDao,
                         final TermHandlerFactory termHandlerFactory) {
        expressionMapper = new CommonExpressionMapper();
        this.metaKeyDao = metaKeyDao;
        this.termHandlerFactory = termHandlerFactory;
    }

    public void map(final QueryField dataSourceField) {
        final Optional<Integer> idOptional = metaKeyDao.getIdForName(dataSourceField.getFldName());

        if (idOptional.isPresent()) {
            final int id = idOptional.get();
            final Field<Long> valueField = createValueField(id);

            final TermHandler<Long> termHandler = termHandlerFactory.create(
                    dataSourceField,
                    valueField,
                    values -> {
                        try {
                            return values.stream()
                                    .filter(str -> !NullSafe.isBlankString(str))
                                    .map(Long::valueOf)
                                    .collect(Collectors.toList());
                        } catch (final NumberFormatException e) {
                            throw new NumberFormatException("Error parsing value \"" +
                                                            values +
                                                            "\" as number for field '" +
                                                            dataSourceField.getFldName() +
                                                            "'");
                        }
                    });

            final MetaTermHandler handler = new MetaTermHandler(
                    createKeyField(id),
                    id,
                    termHandler);
            expressionMapper.addHandler(dataSourceField, handler);
        }
    }

    /**
     * If the criteria contains many terms that come from meta_val then we need to join to meta_val
     * multiple times, each time with a new table alias.
     *
     * @param usedValKeys The list of meta_key IDs that feature in the criteria. One join will be
     *                    added for each.
     * @return The query with joins added
     */
    public <T extends org.jooq.Record> SelectJoinStep<T> addJoins(
            SelectJoinStep<T> query,
            final Field<Long> metaIdField,
            final Set<Integer> usedValKeys) {

        for (final Integer id : usedValKeys) {
            final MetaVal metaVal = getAliasedMetaValTable(id);

            query = query.leftOuterJoin(metaVal)
                    .on(metaIdField.eq(createMetaIdField(id))); //Join on meta_val
        }
        return query;
    }

    /**
     * If the criteria contains many terms that come from meta_val then we need to join to meta_val
     * multiple times, each time with a new table alias.
     *
     * @param usedValKeys The list of meta_key IDs that feature in the criteria. One join will be
     *                    added for each.
     * @return The query with joins added
     */
    public Table<?> addJoins(
            Table<?> fromPart,
            final Field<Long> metaIdField,
            final Set<Integer> usedValKeys) {

        for (final Integer id : usedValKeys) {
            final MetaVal metaVal = getAliasedMetaValTable(id);

            fromPart = fromPart.leftOuterJoin(metaVal)
                    .on(metaIdField.eq(createMetaIdField(id))); //Join on meta_val
        }
        return fromPart;
    }

    private MetaVal getAliasedMetaValTable(final int valKeyId) {
        return MetaVal.META_VAL
                .as(META_ALIAS_PREFIX + valKeyId);
    }

    private Field<Long> createValueField(final int valKeyId) {
        return getAliasedMetaValTable(valKeyId)
                .field(MetaVal.META_VAL.VAL);
    }

    private Field<Integer> createKeyField(final int valKeyId) {
        return getAliasedMetaValTable(valKeyId)
                .field(MetaVal.META_VAL.META_KEY_ID);
    }

    private Field<Long> createMetaIdField(final int valKeyId) {
        return getAliasedMetaValTable(valKeyId)
                .field(MetaVal.META_VAL.META_ID);
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
