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

package stroom.db.util;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class CommonExpressionMapper implements Function<ExpressionItem, Condition> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CommonExpressionMapper.class);

    private final Map<String, Function<ExpressionTerm, Condition>> termHandlers = new HashMap<>();
    private final Map<String, QueryField> fieldMap = new HashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();
    private final Function<ExpressionItem, Condition> delegateItemHandler;

    public CommonExpressionMapper() {
        this.delegateItemHandler = null;
    }

    public CommonExpressionMapper(final Function<ExpressionItem, Condition> delegateItemHandler) {
        this.delegateItemHandler = delegateItemHandler;
    }

    public void addHandler(final QueryField dataSourceField,
                           final Function<ExpressionTerm, Condition> handler) {
        final String fieldName = dataSourceField.getFldName();
        termHandlers.put(fieldName, handler);
        fieldMap.put(fieldName, dataSourceField);
    }

    public void ignoreField(final QueryField dataSourceField) {
        ignoredFields.add(dataSourceField.getFldName());
    }

    /**
     * Converts the passed {@link ExpressionItem} into a Jooq {@link Condition}. By default it
     * will simplify expressions that can be simplified, e.g. NOT {NOT{}} becomes true, an OR
     * with one child that is true becomes true, etc. It will always return a value.
     */
    @Override
    public Condition apply(final ExpressionItem item) {
        LOGGER.debug(() -> item != null
                ? item.toMultiLineString()
                : null);
        final Optional<Condition> optional = innerApply(item);
        LOGGER.debug(() -> optional.map(Object::toString).orElse(null));
        return optional.orElse(DSL.noCondition());
    }

    public Optional<Condition> innerApply(final ExpressionItem item) {
        Optional<Condition> result = Optional.empty();

        if (item != null && item.enabled()) {
            if (item instanceof final ExpressionTerm term) {
                final String fieldName = term.getField();
                if (fieldName == null) {
                    throw new NullPointerException("Term has a null field '" + term + "'");
                }
                if (term.getCondition() == null) {
                    throw new NullPointerException("Term has a null condition '" + term + "'");
                }

                final Function<ExpressionTerm, Condition> termHandler = termHandlers.get(fieldName);
                if (termHandler != null) {
                    final QueryField abstractField = fieldMap.get(fieldName);
                    Objects.requireNonNull(abstractField, () -> LogUtil.message(
                            "abstractField should not be null if we have a termHandler. term: {}", term));

                    // Fields are defined with a list of supported conditions but the code seems to be using
                    // un-supported conditions.
                    if (!abstractField.supportsCondition(term.getCondition())) {
                        LOGGER.debug(() -> LogUtil.message(
                                "Condition '{}' is not supported by field '{}' of type {}. Term: {}",
                                term.getCondition(), fieldName, abstractField.getFldType().getTypeName(), term));
                        if (FieldType.DOC_REF.equals(abstractField.getFldType())) {
                            // https://github.com/gchq/stroom/issues/3074 removed some conditions from DocRefField
                            // instances so log an error
                            LOGGER.error("Condition '{}' is not supported by field '{}' of type {}. Term: {}",
                                    term.getCondition(), fieldName, abstractField.getFldType(), term);
                        }
                    }

                    result = Optional.of(termHandler.apply(term));
                } else if (delegateItemHandler != null) {
                    result = Optional.of(delegateItemHandler.apply(term));
                } else if (!ignoredFields.contains(term.getField())) {
                    throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
                }

            } else if (item instanceof final ExpressionOperator operator) {
                List<ExpressionItem> items = operator.getChildren();
                if (items == null) {
                    items = Collections.emptyList();
                }

                final List<Condition> children = items
                        .stream()
                        .map(this::innerApply)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();

                if (children.size() == 1) {
                    final Condition child = children.getFirst();
                    if (Op.NOT.equals(operator.op())) {
                        result = Optional.of(DSL.not(child));
                    } else {
                        result = Optional.of(child);
                    }

                } else if (!children.isEmpty()) {
                    if (Op.NOT.equals(operator.op())) {
                        result = Optional.of(DSL.not(DSL.and(children)));
                    } else if (Op.AND.equals(operator.op())) {
                        result = Optional.of(DSL.and(children));
                    } else if (Op.OR.equals(operator.op())) {
                        result = Optional.of(DSL.or(children));
                    }
                }
            }
        }

        LOGGER.debug("Converted expressionItem {} into condition {}", item, result);
        return result;
    }
}
