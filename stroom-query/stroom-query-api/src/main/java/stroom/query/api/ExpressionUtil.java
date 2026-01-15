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

package stroom.query.api;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    public static ExpressionOperator equalsCaseSense(final String field, final String value) {
        return ExpressionOperator.builder()
                .addTerm(field, Condition.EQUALS_CASE_SENSITIVE, value)
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

    /**
     * @return True if there are at least one enabled term under the expressionOperator
     */
    public static boolean hasTerms(final ExpressionOperator expressionOperator) {
        if (expressionOperator != null) {
            for (final ExpressionItem child : NullSafe.list(expressionOperator.getChildren())) {
                if (child != null && child.enabled()) {
                    if (child instanceof final ExpressionOperator childOperator) {
                        if (hasTerms(childOperator)) {
                            return true;
                        }
                    } else if (child instanceof ExpressionTerm) {
                        // Found an enabled term so our work is done
                        return true;
                    }
                }
            }
        }
        return false;
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
        return terms(expressionOperator,
                null).stream().map(ExpressionTerm::getValue).collect(Collectors.toList());
    }

    public static List<String> values(final ExpressionOperator expressionOperator, final String fieldName) {
        return terms(expressionOperator,
                Collections.singleton(fieldName)).stream().map(ExpressionTerm::getValue).collect(
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
        // This if condition used to include
        // !Op.NOT.equals(expressionOperator.op()
        // but we have no idea why. If you remember why, then add it back in with a comment
        // explaining why
        if (ExpressionItem.isEnabled(expressionOperator) && expressionOperator.hasChildren()) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof final ExpressionTerm expressionTerm) {
                        if (fieldNames == null || fieldNames.stream()
                                .anyMatch(fieldName ->
                                        fieldName.equals(expressionTerm.getField()) &&
                                        (Condition.IS_DOC_REF.equals(expressionTerm.getCondition()) &&
                                         expressionTerm.getDocRef() != null &&
                                         expressionTerm.getDocRef().getUuid() != null) ||
                                        (expressionTerm.getValue() != null &&
                                         !expressionTerm.getValue().isEmpty()))) {
                            terms.add(expressionTerm);
                        }
                    } else if (item instanceof ExpressionOperator) {
                        addTerms((ExpressionOperator) item, fieldNames, terms);
                    }
                }
            }
        }
    }

    public static ExpressionOperator copyOperator(final ExpressionOperator operator) {
        return copyOperator(operator, null);
    }

    /**
     * Return a deep copy of operator where items are only included in the copy if filter
     * returns true. If an operator is excluded by the filter, then any of its children are
     * also excluded.
     */
    public static ExpressionOperator copyOperator(final ExpressionOperator operator,
                                                  final Predicate<ExpressionItem> filter) {
        if (operator == null) {
            return null;
        }

        final Predicate<ExpressionItem> effectiveFilter = NullSafe.predicate(filter, true);

        if (effectiveFilter.test(operator)) {
            final ExpressionOperator.Builder builder = ExpressionOperator
                    .builder()
                    .enabled(operator.getEnabled())
                    .op(operator.getOp());
            if (operator.getChildren() != null) {
                operator.getChildren()
                        .stream()
                        .filter(effectiveFilter)
                        .forEach(item -> {
                            ExpressionItem childCopy = null;
                            if (item instanceof ExpressionOperator) {
                                childCopy = copyOperator((ExpressionOperator) item, effectiveFilter);

                            } else if (item instanceof ExpressionTerm) {
                                childCopy = copyTerm((ExpressionTerm) item, effectiveFilter);
                            }
                            NullSafe.consume(childCopy, builder::addItem);
                        });
            }
            return builder.build();
        } else {
            return null;
        }
    }

    public static ExpressionTerm copyTerm(final ExpressionTerm term) {
        return copyTerm(term, null);
    }

    /**
     * Return a copy of term if filter returns true.
     */
    public static ExpressionTerm copyTerm(final ExpressionTerm term,
                                          final Predicate<ExpressionItem> filter) {
        if (term == null) {
            return null;
        }
        final Predicate<ExpressionItem> effectiveFilter = NullSafe.predicate(filter, true);
        if (effectiveFilter.test(term)) {
            return ExpressionTerm
                    .builder()
                    .enabled(term.getEnabled())
                    .field(term.getField())
                    .condition(term.getCondition())
                    .value(term.getValue())
                    .docRef(term.getDocRef())
                    .build();
        } else {
            return null;
        }
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
                final ParamValues paramValues = ParamUtil.createParamValueFunction(query.getParams());
                expression = replaceExpressionParameters(expression, paramValues, false);
            }
            result = query.copy().expression(expression).build();
        }
        return result;
    }

    public static ExpressionOperator replaceExpressionParameters(final ExpressionOperator operator,
                                                                 final List<Param> params) {
        final ExpressionOperator result;
        if (operator != null) {
            final ParamValues paramValues = ParamUtil.createParamValueFunction(params);
            result = replaceExpressionParameters(operator, paramValues, false);
        } else {
            result = null;
        }
        return result;
    }

    public static ExpressionOperator replaceExpressionParameters(final ExpressionOperator operator,
                                                                 final ParamValues paramValues,
                                                                 final boolean keepUnmatched) {
        final ExpressionOperator.Builder builder = ExpressionOperator
                .builder()
                .enabled(operator.getEnabled())
                .op(operator.getOp());
        if (operator.getChildren() != null) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child instanceof final ExpressionOperator childOperator) {
                    builder.addOperator(replaceExpressionParameters(childOperator, paramValues, keepUnmatched));

                } else if (child instanceof final ExpressionTerm term) {
                    final String value = term.getValue();
                    final String replaced = ParamUtil
                            .replaceTermValueParameters(value, paramValues, keepUnmatched);
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
                if (expressionItem instanceof final ExpressionOperator expressionOperator) {
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

    /**
     * Simplify an expression to improve comprehension and make processing faster.
     *
     * @param item The expression to simplify.
     * @return The simplified expression.
     */
    public static ExpressionOperator simplify(final ExpressionOperator item) {
        final ExpressionItem expressionItem = simplifyExpressionItem(item);
        if (expressionItem == null) {
            return null;
        }
        if (expressionItem instanceof ExpressionOperator) {
            return (ExpressionOperator) expressionItem;
        }
        return ExpressionOperator.builder()
                .op(Op.AND)
                .children(Collections.singletonList(expressionItem))
                .build();
    }

    private static ExpressionItem simplifyExpressionItem(final ExpressionItem item) {
        // Remove null or disabled.
        if (item == null || !item.enabled()) {
            return null;
        }

        if (item instanceof final ExpressionOperator operator) {

            // Remove empty children.
            final List<ExpressionItem> children = operator.getChildren();
            if (children == null || children.isEmpty()) {
                return null;
            }

            final List<ExpressionItem> simplifiedChildren = new ArrayList<>(children.size());
            for (final ExpressionItem child : children) {
                final ExpressionItem simplifiedChild = simplifyExpressionItem(child);
                if (simplifiedChild != null) {
                    if (simplifiedChild instanceof final ExpressionOperator childOperator) {
                        if (childOperator.getChildren() != null && !childOperator.getChildren().isEmpty()) {
                            if (childOperator.getChildren().size() == 1) {
                                if (!Op.NOT.equals(operator.op()) && !Op.NOT.equals(childOperator.op())) {
                                    // Simplify AND(AND()) or AND(OR()) or OR(AND()) or OR(OR())
                                    simplifiedChildren.add(childOperator.getChildren().get(0));
                                } else if (Op.NOT.equals(operator.op()) && Op.NOT.equals(childOperator.op())) {
                                    // Simplify NOT(NOT())
                                    simplifiedChildren.add(childOperator.getChildren().get(0));
                                } else {
                                    simplifiedChildren.add(childOperator);
                                }
                            } else {
                                simplifiedChildren.add(childOperator);
                            }
                        }
                    } else {
                        simplifiedChildren.add(simplifiedChild);
                    }
                }
            }

            if (simplifiedChildren.isEmpty()) {
                return null;
            }

            return ExpressionOperator.builder().op(operator.op()).children(simplifiedChildren).build();
        }

        return item;
    }

    public static ExpressionOperator combine(final ExpressionOperator in,
                                             final ExpressionOperator decoration) {
        if (in == null) {
            return decoration;
        } else if (decoration == null) {
            return in;
        }

        return ExpressionOperator
                .builder()
                .addOperator(in)
                .addOperator(decoration)
                .build();
    }


    // --------------------------------------------------------------------------------


    public interface ExpressionItemVisitor {

        /**
         * @return False to stop walking the tree
         */
        boolean visit(final ExpressionItem expressionItem);
    }
}
