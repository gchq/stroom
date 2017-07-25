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
 */

package stroom.entity.server;

import event.logging.BaseAdvancedQueryItem;
import event.logging.BaseAdvancedQueryOperator;
import event.logging.TermCondition;
import event.logging.util.EventLoggingUtil;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.dictionary.shared.FindDictionaryCriteria;
import stroom.query.api.v1.ExpressionItem;
import stroom.query.api.v1.ExpressionOperator;
import stroom.query.api.v1.ExpressionOperator.Op;
import stroom.query.api.v1.ExpressionTerm;

import java.util.List;

public class QueryDataLogUtil {
    public static void appendExpressionItem(final List<BaseAdvancedQueryItem> items,
                                            final DictionaryService dictionaryService, final ExpressionItem item) {
        if (item == null) {
            return;
        }

        if (item.enabled()) {
            if (item instanceof ExpressionOperator) {
                appendOperator(items, dictionaryService, (ExpressionOperator) item);
            } else {
                final ExpressionTerm expressionTerm = (ExpressionTerm) item;

                final String field = expressionTerm.getField();
                String value = expressionTerm.getValue();

                switch (expressionTerm.getCondition()) {
                    case EQUALS:
                        appendTerm(items, field, TermCondition.EQUALS, value);
                        break;
                    case CONTAINS:
                        appendTerm(items, field, TermCondition.CONTAINS, value);
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
                        appendTerm(items, field, TermCondition.CONTAINS, value);
                        break;
                    case IN:
                        appendTerm(items, field, TermCondition.CONTAINS, value);
                        break;
                    case IN_DICTIONARY:
                        if (dictionaryService != null) {
                            final FindDictionaryCriteria dictionaryCriteria = new FindDictionaryCriteria();
                            dictionaryCriteria.getName().setString(expressionTerm.getValue());
                            final List<Dictionary> dictionaries = dictionaryService.find(dictionaryCriteria);
                            if (dictionaries != null && dictionaries.size() > 0) {
                                final Dictionary dictionary = dictionaries.get(0);
                                final String words = dictionary.getData();
                                if (words != null) {
                                    value += " (" + words + ")";
                                }
                            }

                            appendTerm(items, field, TermCondition.CONTAINS, value);

                        } else {
                            appendTerm(items, field, TermCondition.CONTAINS, "dictionary: " + value);
                        }
                        break;
                }
            }
        }
    }

    private static void appendOperator(final List<BaseAdvancedQueryItem> items,
                                       final DictionaryService dictionaryService, final ExpressionOperator exp) {
        BaseAdvancedQueryOperator operator;
        if (exp.getOp() == Op.NOT) {
            operator = new BaseAdvancedQueryOperator.Not();
        } else if (exp.getOp() == Op.OR) {
            operator = new BaseAdvancedQueryOperator.Or();
        } else {
            operator = new BaseAdvancedQueryOperator.And();
        }

        items.add(operator);

        if (exp.getChildren() != null) {
            for (final ExpressionItem child : exp.getChildren()) {
                appendExpressionItem(operator.getAdvancedQueryItems(), dictionaryService, child);
            }
        }
    }

    private static void appendTerm(final List<BaseAdvancedQueryItem> items, String field, TermCondition condition,
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
