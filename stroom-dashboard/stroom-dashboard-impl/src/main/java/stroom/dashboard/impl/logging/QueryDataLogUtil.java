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

package stroom.dashboard.impl.logging;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;

import event.logging.AdvancedQueryItem;
import event.logging.AdvancedQueryOperator;
import event.logging.And;
import event.logging.Not;
import event.logging.Or;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class QueryDataLogUtil {

    public static void appendExpressionItem(final List<AdvancedQueryItem> items,
                                            final WordListProvider wordListProvider,
                                            final CollectionService collectionService,
                                            final ExpressionItem item) {
        if (item == null) {
            return;
        }

        if (item.enabled()) {
            if (item instanceof ExpressionOperator) {
                appendOperator(items, wordListProvider, collectionService, (ExpressionOperator) item);
            } else {
                final ExpressionTerm expressionTerm = (ExpressionTerm) item;

                final String field = expressionTerm.getField();
                String value = expressionTerm.getValue();

                switch (expressionTerm.getCondition()) {
                    case EQUALS:
                        appendTerm(items, field, TermCondition.EQUALS, value);
                        break;
                    case NOT_EQUALS:
                        appendTerm(items, field, TermCondition.NOT_EQUALS, value);
                        break;
                    case CONTAINS:
                        appendTerm(items, field, TermCondition.EQUALS, value);
                        break;
                    case GREATER_THAN:
                        appendTerm(items, field, TermCondition.GREATER_THAN, value);
                        break;
                    case GREATER_THAN_OR_EQUAL_TO:
                        appendTerm(items, field, TermCondition.GREATER_THAN_EQUAL_TO, value);
                        break;
                    case LESS_THAN:
                        appendTerm(items, field, TermCondition.LESS_THAN, value);
                        break;
                    case LESS_THAN_OR_EQUAL_TO:
                        appendTerm(items, field, TermCondition.LESS_THAN_EQUAL_TO, value);
                        break;
                    case BETWEEN:
                        appendTerm(items, field, TermCondition.EQUALS, value);
                        break;
                    case IN:
                        appendTerm(items, field, TermCondition.EQUALS, value);
                        break;
                    case IN_DICTIONARY:
                        if (wordListProvider != null) {
                            DocRef docRef = expressionTerm.getDocRef();
                            if (docRef == null) {
                                final List<DocRef> docRefs = wordListProvider.findByName(expressionTerm.getValue());
                                if (docRefs != null && docRefs.size() > 0) {
                                    docRef = docRefs.get(0);
                                }
                            }

                            if (docRef != null) {
                                final String words = wordListProvider.getCombinedData(docRef);
                                value += " (" + words + ")";

                                appendTerm(items, field, TermCondition.EQUALS, value);

                            } else {
                                appendTerm(items, field, TermCondition.EQUALS, "dictionary: " + value);
                            }

                        } else {
                            appendTerm(items, field, TermCondition.EQUALS, "dictionary: " + value);
                        }
                        break;
                    case IN_FOLDER:
                        if (collectionService != null) {
                            final Set<DocRef> docRefs = collectionService.getDescendants(expressionTerm.getDocRef(),
                                    expressionTerm.getField());
                            if (docRefs != null && docRefs.size() > 0) {
                                final String words = docRefs
                                        .stream()
                                        .map(DocRef::getUuid)
                                        .collect(Collectors.joining(","));
                                value += " (" + words + ")";
                                appendTerm(items, field, TermCondition.EQUALS, value);

                            } else {
                                appendTerm(items, field, TermCondition.EQUALS, "folder: " + value);
                            }

                        } else {
                            appendTerm(items, field, TermCondition.EQUALS, "folder: " + value);
                        }
                        break;
                    case IS_DOC_REF: {
                        final DocRef docRef = expressionTerm.getDocRef();
                        if (docRef != null) {
                            appendTerm(items, field, TermCondition.EQUALS, docRef.toInfoString());
                        }
                        break;
                    }
                }
            }
        }
    }

    private static void appendOperator(final List<AdvancedQueryItem> items,
                                       final WordListProvider wordListProvider,
                                       final CollectionService collectionService,
                                       final ExpressionOperator exp) {
        final AdvancedQueryOperator operator;
        if (exp.op() == Op.NOT) {
            operator = new Not();
        } else if (exp.op() == Op.OR) {
            operator = new Or();
        } else {
            operator = new And();
        }

        items.add(operator);

        if (exp.getChildren() != null) {
            for (final ExpressionItem child : exp.getChildren()) {
                appendExpressionItem(operator.getQueryItems(), wordListProvider, collectionService, child);
            }
        }
    }

    private static void appendTerm(final List<AdvancedQueryItem> items,
                                   String field,
                                   TermCondition condition,
                                   String value) {
        if (field == null) {
            field = "";
        }
        if (condition == null) {
            condition = TermCondition.EQUALS;
        }
        if (value == null) {
            value = "";
        }
        items.add(EventLoggingUtil.createTerm(field, condition, value));
    }
}
