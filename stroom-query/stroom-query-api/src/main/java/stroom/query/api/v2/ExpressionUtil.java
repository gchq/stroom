package stroom.query.api.v2;

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.GwtNullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("PatternVariableCanBeUsed") // Cos GWT :-(
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

    /**
     * @return True if there are at least one enabled term under the expressionOperator
     */
    public static boolean hasTerms(final ExpressionOperator expressionOperator) {
        if (expressionOperator != null) {
            for (final ExpressionItem child : GwtNullSafe.list(expressionOperator.getChildren())) {
                if (child != null && child.enabled()) {
                    if (child instanceof ExpressionOperator) {
                        final ExpressionOperator childOperator = (ExpressionOperator) child;
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
        return terms(expressionOperator,
                null).stream().map(ExpressionTerm::getField).collect(Collectors.toList());
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
                    if (item instanceof ExpressionTerm) {
                        final ExpressionTerm expressionTerm = (ExpressionTerm) item;
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
                final Map<String, String> paramMap = ParamUtil.createParamMap(query.getParams());
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
            final Map<String, String> paramMap = ParamUtil.createParamMap(params);
            result = replaceExpressionParameters(operator, paramMap);
        } else {
            result = null;
        }
        return result;
    }

    public static ExpressionOperator replaceExpressionParameters(final ExpressionOperator operator,
                                                                 final Map<String, String> paramMap) {
        final ExpressionOperator.Builder builder = ExpressionOperator
                .builder()
                .enabled(operator.getEnabled())
                .op(operator.getOp());
        if (operator.getChildren() != null) {
            for (ExpressionItem child : operator.getChildren()) {
                if (child instanceof ExpressionOperator) {
                    final ExpressionOperator childOperator = (ExpressionOperator) child;
                    builder.addOperator(replaceExpressionParameters(childOperator, paramMap));

                } else if (child instanceof ExpressionTerm) {
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

        if (item instanceof ExpressionOperator) {
            final ExpressionOperator operator = (ExpressionOperator) item;

            // Remove empty children.
            final List<ExpressionItem> children = operator.getChildren();
            if (children == null || children.isEmpty()) {
                return null;
            }

            final List<ExpressionItem> simplifiedChildren = new ArrayList<>(children.size());
            for (final ExpressionItem child : children) {
                final ExpressionItem simplifiedChild = simplifyExpressionItem(child);
                if (simplifiedChild != null) {
                    if (simplifiedChild instanceof ExpressionOperator) {
                        final ExpressionOperator childOperator = (ExpressionOperator) simplifiedChild;
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
