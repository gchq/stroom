/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.event.logging.api;

import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageResponse;
import stroom.util.shared.Selection;

import event.logging.AdvancedQuery;
import event.logging.AdvancedQueryItem;
import event.logging.And;
import event.logging.Not;
import event.logging.Or;
import event.logging.Query;
import event.logging.SimpleQuery;
import event.logging.Term;
import event.logging.TermCondition;

import java.util.stream.Collectors;

public interface DocumentEventLog {

    void create(Object entity, Throwable ex);

    void create(String entityType, String entityName, Throwable ex);

    void copy(Object before, Object after, Throwable ex);

    void move(Object before, Object after, Throwable ex);

    void rename(Object before, Object after, Throwable ex);

    void delete(Object entity, Throwable ex);

    void update(Object before, Object after, Throwable ex);

    void view(Object entity, Throwable ex);

    void delete(BaseCriteria criteria, Query query, Long size, Throwable ex);

    void download(Object entity, Throwable ex);

//    void search(BaseCriteria criteria, Query query, String resultType, BaseResultList<?> results, Throwable ex);

    void search(String typeId, Query query, String resultType, PageResponse pageResponse, Throwable ex);

//    void searchSummary(BaseCriteria criteria, Query query, String resultType, BaseResultList<?> results, Throwable ex);

    static void appendSelection(final Query.Builder<Void> queryBuilder, final Selection<?> selection) {
        if (selection != null) {
            if (selection.isMatchAll()) {
                queryBuilder
                        .withAdvanced(AdvancedQuery.builder()
                                .addAnd(And.builder()
                                        .build())
                                .build());
            } else if (selection.isMatchNothing()) {
                queryBuilder
                        .withAdvanced(AdvancedQuery.builder()
                                .addNot(Not.builder()
                                        .build())
                                .build());
            } else {
                queryBuilder.withSimple(SimpleQuery.builder()
                        .withInclude(selection.toString())
                        .build());
            }
        }
    }

    static void appendExpression(final Query.Builder<Void> queryBuilder, final ExpressionItem expressionItem) {
        queryBuilder.withAdvanced(AdvancedQuery.builder()
                .withQueryItems(convertItem(expressionItem))
                        .build());
    }

    private static AdvancedQueryItem convertItem(final ExpressionItem expressionItem) {
        if (expressionItem instanceof ExpressionTerm && expressionItem.enabled()) {
            final ExpressionTerm expressionTerm = (ExpressionTerm) expressionItem;

            return Term.builder()
                    .withName(expressionTerm.getField())
                    .withCondition(convertCondition(expressionTerm.getCondition()))
                    .withValue(expressionTerm.getValue())
                    .build();
        } else if (expressionItem instanceof ExpressionOperator && expressionItem.enabled()) {
            final ExpressionOperator expressionOperator = (ExpressionOperator) expressionItem;
            final AdvancedQueryItem operator;
            if (expressionOperator.op().equals(Op.AND)) {
                operator = And.builder()
                        .withQueryItems(expressionOperator.getChildren()
                                .stream()
                                .map(DocumentEventLog::convertItem)
                                .collect(Collectors.toList()))
                        .build();
            } else if (expressionOperator.op().equals(Op.OR)) {
                operator = Or.builder()
                        .withQueryItems(expressionOperator.getChildren()
                                .stream()
                                .map(DocumentEventLog::convertItem)
                                .collect(Collectors.toList()))
                        .build();
            } else if (expressionOperator.op().equals(Op.NOT)) {
                operator = Not.builder()
                        .withQueryItems(expressionOperator.getChildren()
                                .stream()
                                .map(DocumentEventLog::convertItem)
                                .collect(Collectors.toList()))
                        .build();
            } else {
                throw new RuntimeException("Unknown op " + expressionOperator.op());
            }
            return operator;
        } else {
            throw new RuntimeException("Unknown type " + expressionItem.getClass().getName());
        }
    }

    private static TermCondition convertCondition(final Condition condition) {
        final TermCondition termCondition;
        switch (condition) {
            case CONTAINS:
                termCondition = TermCondition.CONTAINS;
                break;
            case EQUALS:
                termCondition = TermCondition.EQUALS;
                break;
            case GREATER_THAN:
                termCondition = TermCondition.GREATER_THAN;
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                termCondition = TermCondition.GREATER_THAN_EQUAL_TO;
                break;
            case LESS_THAN:
                termCondition = TermCondition.LESS_THAN;
                break;
            case LESS_THAN_OR_EQUAL_TO:
                termCondition = TermCondition.LESS_THAN_EQUAL_TO;
                break;
            default:
                throw new RuntimeException("Can't convert condition " + condition);
        }
        return termCondition;
    }
}
