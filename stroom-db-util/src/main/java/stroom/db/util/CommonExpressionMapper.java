/*
 * Copyright 2024 Crown Copyright
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

import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.string.CIKey;

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
import java.util.stream.Collectors;

public final class CommonExpressionMapper implements Function<ExpressionItem, Condition> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CommonExpressionMapper.class);

    private final Map<CIKey, Function<ExpressionTerm, Condition>> termHandlers = new HashMap<>();
    private final Map<CIKey, QueryField> fieldMap = new HashMap<>();
    private final Set<CIKey> ignoredFields = new HashSet<>();
    private final Function<ExpressionItem, Condition> delegateItemHandler;

    public CommonExpressionMapper() {
        this.delegateItemHandler = null;
    }

    public CommonExpressionMapper(final Function<ExpressionItem, Condition> delegateItemHandler) {
        this.delegateItemHandler = delegateItemHandler;
    }

    public void addHandler(final QueryField dataSourceField,
                           final Function<ExpressionTerm, Condition> handler) {
        final CIKey fieldName = dataSourceField.getFldNameAsCIKey();
        termHandlers.put(fieldName, handler);
        fieldMap.put(fieldName, dataSourceField);
    }

    public void ignoreField(final QueryField dataSourceField) {
        ignoredFields.add(dataSourceField.getFldNameAsCIKey());
    }

    /**
     * Converts the passed {@link ExpressionItem} into a Jooq {@link Condition}. By default, it
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
                final CIKey fieldName = CIKey.of(term.getField());
                if (CIKey.isNull(fieldName)) {
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
                } else if (!ignoredFields.contains(fieldName)) {
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
                        .collect(Collectors.toList());

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
