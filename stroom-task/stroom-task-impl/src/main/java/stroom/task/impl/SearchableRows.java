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

package stroom.task.impl;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.util.NullSafe;
import stroom.util.PredicateUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class SearchableRows implements Searchable {

    private static final int MAX_ROWS = 100_000;

    private static final DocRef DOC_REF = new DocRef(
            "Searchable",
            "Rows",
            "Rows");

    private static final QueryField ROW_NUM_FIELD = QueryField.builder()
            .fldName(CIKey.ofStaticKey("RowNum"))
            .fldType(FieldType.INTEGER)
            .conditionSet(ConditionSet.DEFAULT_NUMERIC)
            .queryable(true)
            .build();

    private static final List<QueryField> FIELDS = Collections.singletonList(ROW_NUM_FIELD);

    private static final ExpressionOperator EMPTY_OPERATOR = ExpressionOperator.builder().build();
    private static final Predicate<Integer> TRUE_PREDICATE = val -> true;
    private static final Predicate<Integer> FALSE_PREDICATE = val -> false;

    private final SizesProvider sizesProvider;

    @Inject
    public SearchableRows(final SizesProvider sizesProvider) {
        this.sizesProvider = sizesProvider;
    }

    @Override
    public DocRef getDocRef() {
        return DOC_REF;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return FieldInfoResultPageBuilder.builder(criteria)
                .addAll(FIELDS)
                .build();
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return 1;
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public QueryField getTimeField() {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        final List<CIKey> fields = fieldIndex.getFieldsAsCIKeys();
        final PageRequest pageRequest = criteria.getPageRequest();
        final int offset = NullSafe.getOrElse(pageRequest, PageRequest::getOffset, 0);
        final int pageLen = NullSafe.getOrElse(pageRequest, PageRequest::getLength, MAX_ROWS);
        final int maxOffsetExc = offset + pageLen;
        final CIKey rowNumKey = ROW_NUM_FIELD.getFldNameAsCIKey();
        final int rowNumFieldIdx = fields.indexOf(rowNumKey);
        final Sizes sizes = sizesProvider.getDefaultMaxResultsSizes();
        final long maxRows = Math.min(sizes.size(0), MAX_ROWS);

        final Predicate<Integer> predicate = createPredicate(criteria);

        // TODO fix sorting
        int rowNum;
        for (int i = offset; i < maxOffsetExc; i++) {
            // Convert the values into one based
            rowNum = i + 1;
            if (rowNum > maxRows) {
                break;
            }
            if (predicate == null || predicate.test(rowNum)) {
                final Val[] valArr = new Val[fields.size()];
                valArr[rowNumFieldIdx] = ValInteger.create(rowNum);
                consumer.accept(valArr);
            }
        }
    }

    private Predicate<Integer> createPredicate(final ExpressionCriteria criteria) {
        final ExpressionOperator expression = criteria.getExpression();
        if (expression == null || EMPTY_OPERATOR.equals(expression)) {
            return null;
        } else {
            if (expression.enabled()) {
                return createPredicate(criteria.getExpression());
            } else {
                return null;
            }
        }
    }

    private Predicate<Integer> createPredicate(final ExpressionOperator expressionOperator) {
        final List<ExpressionItem> children = expressionOperator.getChildren();
        final List<Predicate<Integer>> childPredicates = new ArrayList<>(children.size());
        for (final ExpressionItem expressionItem : children) {
            if (expressionItem != null && expressionItem.enabled()) {
                final Predicate<Integer> childPredicate;
                if (expressionItem instanceof ExpressionOperator childOperator) {
                    childPredicate = createPredicate(childOperator);
                } else if (expressionItem instanceof ExpressionTerm childTerm) {
                    childPredicate = createPredicate(childTerm);
                } else {
                    throw new IllegalArgumentException("Unknown type " + expressionItem.getClass());
                }
                NullSafe.consume(childPredicate, childPredicates::add);
            }
        }
        if (children.isEmpty()) {
            return null;
        } else {
            return switch (expressionOperator.op()) {
                case OR -> PredicateUtil.orPredicates(childPredicates, null);
                case AND -> PredicateUtil.andPredicates(childPredicates, null);
                case NOT -> {
                    final Predicate<Integer> predicate = PredicateUtil.andPredicates(childPredicates, null);
                    if (predicate != null) {
                        yield Predicate.not(predicate);
                    } else {
                        yield null;
                    }
                }
            };
        }
    }

    private Predicate<Integer> createPredicate(final ExpressionTerm expressionTerm) {
        if (expressionTerm == null || !expressionTerm.enabled()) {
            return null;
        } else {
            final String value = expressionTerm.getValue();
            Objects.requireNonNull(value);
            final Condition condition = expressionTerm.getCondition();
            return switch (condition) {
                case EQUALS -> i -> i == valToInt(value);
                case NOT_EQUALS -> i -> i != valToInt(value);
                case BETWEEN -> {
                    final String[] values = value.split(",");
                    final int[] numbers = new int[values.length];
                    for (int j = 0; j < values.length; j++) {
                        numbers[j] = Integer.parseInt(values[j].trim());
                    }
                    yield i -> i >= numbers[0] && i <= numbers[1];
                }
                case GREATER_THAN -> i -> i > valToInt(value);
                case GREATER_THAN_OR_EQUAL_TO -> i -> i >= valToInt(value);
                case LESS_THAN -> i -> i < valToInt(value);
                case LESS_THAN_OR_EQUAL_TO -> i -> i <= valToInt(value);
                default -> throw new UnsupportedOperationException("Condition " + condition + " not supported");
            };
        }
    }

    private int valToInt(final String val) {
        return Integer.parseInt(Objects.requireNonNull(val));
    }
}
