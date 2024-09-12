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

package stroom.query.api.v2;

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExpressionUtil {

    private ExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator equals(final String field, final String value) {
        return ExpressionOperator.builder()
                .addTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsBoolean(final QueryField field, final boolean value) {
        return ExpressionOperator.builder()
                .addBooleanTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsDate(final QueryField field, final String value) {
        return ExpressionOperator.builder()
                .addDateTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsDocRef(final QueryField field, final DocRef value) {
        return ExpressionOperator.builder()
                .addDocRefTerm(field, Condition.IS_DOC_REF, value)
                .build();
    }

    public static ExpressionOperator equalsId(final QueryField field, final long value) {
        return ExpressionOperator.builder()
                .addIdTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsInteger(final QueryField field, final int value) {
        return ExpressionOperator.builder()
                .addIntegerTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsLong(final QueryField field, final long value) {
        return ExpressionOperator.builder()
                .addLongTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsFloat(final QueryField field, final float value) {
        return ExpressionOperator.builder()
                .addFloatTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsDouble(final QueryField field, final double value) {
        return ExpressionOperator.builder()
                .addDoubleTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsText(final QueryField field, final String value) {
        return ExpressionOperator.builder()
                .addTextTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static ExpressionOperator equalsDocRef(final String field, final DocRef value) {
        return ExpressionOperator.builder()
                .addDocRefTerm(field, Condition.EQUALS, value)
                .build();
    }

    public static int termCount(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null).size();
    }

    public static int termCount(final ExpressionOperator expressionOperator,
                                final String fieldName) {
        return termCount(expressionOperator, Collections.singleton(fieldName));
    }

    public static int termCount(final ExpressionOperator expressionOperator,
                                final Collection<String> fieldNames) {
        return terms(expressionOperator, fieldNames).size();
    }

    public static List<String> fields(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null)
                .stream()
                .map(ExpressionTerm::getField)
                .collect(Collectors.toList());
    }

    public static List<String> values(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null)
                .stream()
                .map(ExpressionTerm::getValue)
                .collect(Collectors.toList());
    }

    public static List<String> values(final ExpressionOperator expressionOperator, final String fieldName) {
        return terms(expressionOperator, Collections.singleton(fieldName))
                .stream()
                .map(ExpressionTerm::getValue).collect(
                        Collectors.toList());
    }

    public static List<ExpressionTerm> terms(final ExpressionOperator expressionOperator,
                                             final Collection<String> fieldNames) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        addTerms(expressionOperator, fieldNames, terms);
        return terms;
    }

    private static void addTerms(final ExpressionOperator expressionOperator,
                                 final Collection<String> fieldNames,
                                 final List<ExpressionTerm> terms) {
        if (expressionOperator != null &&
                expressionOperator.enabled() &&
                expressionOperator.getChildren() != null &&
                !Op.NOT.equals(expressionOperator.op())) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof ExpressionTerm) {
                        //noinspection PatternVariableCanBeUsed // GWT
                        final ExpressionTerm term = (ExpressionTerm) item;
                        if (fieldNames == null || fieldNames.stream()
                                .anyMatch(fieldName ->
                                        fieldName.equalsIgnoreCase(term.getField()) &&
                                                (Condition.IS_DOC_REF.equals(term.getCondition()) &&
                                                        term.getDocRef() != null &&
                                                        term.getDocRef().getUuid() != null) ||
                                                (GwtNullSafe.isNonEmptyString(term.getValue())))) {
                            terms.add(term);
                        }
                    } else if (item instanceof ExpressionOperator) {
                        addTerms((ExpressionOperator) item, fieldNames, terms);
                    }
                }
            }
        }
    }

    public static ExpressionOperator copyOperator(final ExpressionOperator operator) {
        if (operator == null) {
            return null;
        }

        final ExpressionOperator.Builder builder = ExpressionOperator
                .builder()
                .enabled(operator.getEnabled())
                .op(operator.getOp());
        if (operator.getChildren() != null) {
            operator.getChildren().forEach(item -> {
                if (item instanceof ExpressionOperator) {
                    builder.addOperator(copyOperator((ExpressionOperator) item));

                } else if (item instanceof ExpressionTerm) {
                    builder.addTerm(copyTerm((ExpressionTerm) item));
                }
            });
        }
        return builder.build();
    }

    public static ExpressionTerm copyTerm(final ExpressionTerm term) {
        if (term == null) {
            return null;
        }

        return ExpressionTerm
                .builder()
                .enabled(term.getEnabled())
                .field(term.getField())
                .condition(term.getCondition())
                .value(term.getValue())
                .docRef(term.getDocRef())
                .build();
    }

    public static SearchRequest replaceExpressionParameters(final SearchRequest searchRequest) {
        SearchRequest result = searchRequest;
        if (searchRequest != null && searchRequest.getQuery() != null) {
            final Query query = replaceExpressionParameters(searchRequest.getQuery());
            result = searchRequest.copy().query(query).build();
        }
        return result;
    }

    public static Query replaceExpressionParameters(final Query query) {
        Query result = query;
        if (query != null) {
            ExpressionOperator expression = query.getExpression();
            if (query.getParams() != null && expression != null) {
                final Map<CIKey, String> paramMap = ParamUtil.createParamMap(query.getParams());
                expression = replaceExpressionParameters(expression, paramMap);
            }
            result = query.copy().expression(expression).build();
        }
        return result;
    }

    public static ExpressionOperator replaceExpressionParameters(final ExpressionOperator operator,
                                                                 final List<Param> params) {
        final ExpressionOperator result;
        if (operator != null) {
            final Map<CIKey, String> paramMap = ParamUtil.createParamMap(params);
            result = replaceExpressionParameters(operator, paramMap);
        } else {
            result = null;
        }
        return result;
    }

    public static ExpressionOperator replaceExpressionParameters(final ExpressionOperator operator,
                                                                 final Map<CIKey, String> paramMap) {
        final ExpressionOperator.Builder builder = ExpressionOperator
                .builder()
                .enabled(operator.getEnabled())
                .op(operator.getOp());
        if (operator.getChildren() != null) {
            for (ExpressionItem child : operator.getChildren()) {
                if (child instanceof ExpressionOperator) {
                    //noinspection PatternVariableCanBeUsed // GWT
                    final ExpressionOperator childOperator = (ExpressionOperator) child;
                    builder.addOperator(replaceExpressionParameters(childOperator, paramMap));

                } else if (child instanceof ExpressionTerm) {
                    //noinspection PatternVariableCanBeUsed // GWT
                    final ExpressionTerm term = (ExpressionTerm) child;
                    final String value = term.getValue();
                    final String replaced = ParamUtil.replaceParameters(value, paramMap);
                    builder.addTerm(ExpressionTerm.builder()
                            .enabled(term.enabled())
                            .field(term.getField())
                            .condition(term.getCondition())
                            .value(replaced)
                            .docRef(term.getDocRef())
                            .build());
                }
            }
        }
        return builder.build();
    }

    public static boolean validateExpressionTerms(final ExpressionItem expressionItem,
                                                  final Predicate<ExpressionTerm> predicate) {
        final AtomicBoolean isValid = new AtomicBoolean(false);
        walkExpressionTree(expressionItem, expressionItem2 -> {
            if (expressionItem2 instanceof ExpressionTerm) {
                isValid.set(predicate.test((ExpressionTerm) expressionItem2));
                return isValid.get();
            } else {
                return true;
            }
        });
        return isValid.get();
    }

    public static boolean walkExpressionTree(final ExpressionItem expressionItem,
                                             final ExpressionItemVisitor itemVisitor) {

        boolean continueWalking = true;
        if (expressionItem != null) {
            if (itemVisitor != null) {
                continueWalking = itemVisitor.visit(expressionItem);
            }
            if (continueWalking) {
                if (expressionItem instanceof ExpressionOperator) {
                    //noinspection PatternVariableCanBeUsed // GWT
                    final ExpressionOperator expressionOperator = (ExpressionOperator) expressionItem;
                    final List<ExpressionItem> children = expressionOperator.getChildren();
                    if (children != null && !children.isEmpty()) {
                        for (final ExpressionItem child : children) {
                            continueWalking = walkExpressionTree(child, itemVisitor);
                            if (!continueWalking) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return continueWalking;
    }


    // --------------------------------------------------------------------------------


    public interface ExpressionItemVisitor {

        /**
         * @param expressionItem
         * @return False to stop walking the tree
         */
        boolean visit(final ExpressionItem expressionItem);
    }
}
