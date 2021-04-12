package stroom.db.util;

import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.or;

public final class CommonExpressionMapper implements Function<ExpressionItem, Condition> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CommonExpressionMapper.class);

    private final Map<String, Function<ExpressionTerm, Condition>> termHandlers = new HashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();
    private final Function<ExpressionItem, Condition> delegateItemHandler;

    public CommonExpressionMapper() {
        this.delegateItemHandler = null;
    }

    public CommonExpressionMapper(final Function<ExpressionItem, Condition> delegateItemHandler) {
        this.delegateItemHandler = delegateItemHandler;
    }

    public void addHandler(final AbstractField dataSourceField,
                           final Function<ExpressionTerm, Condition> handler) {
        termHandlers.put(dataSourceField.getName(), handler);
    }

    public void ignoreField(final AbstractField dataSourceField) {
        ignoredFields.add(dataSourceField.getName());
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
        final Condition conditions = apply(item, true);
        LOGGER.debug(() -> conditions != null
                ? conditions.toString()
                : null);
        return conditions;
    }

    public Condition apply(final ExpressionItem item,
                           final boolean simplifyConditions) {
        Condition result = null;

        if (item != null && item.enabled()) {
            if (item instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) item;
                if (term.getField() == null) {
                    throw new NullPointerException("Term has a null field '" + term + "'");
                }
                if (term.getCondition() == null) {
                    throw new NullPointerException("Term has a null condition '" + term + "'");
                }

                final Function<ExpressionTerm, Condition> termHandler = termHandlers.get(term.getField());
                if (termHandler != null) {
                    result = termHandler.apply(term);
                } else if (delegateItemHandler != null) {
                    result = delegateItemHandler.apply(term);
                } else if (!ignoredFields.contains(term.getField())) {
                    throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
                }

            } else if (item instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) item;
                if (operator.getChildren() != null && !operator.getChildren().isEmpty()) {
                    final Collection<Condition> children = operator.getChildren()
                            .stream()
                            .map(expressionItem -> apply(expressionItem, simplifyConditions))
                            .collect(Collectors.toList());

                    result = switch (operator.op()) {
                        case AND -> buildAndConditions(simplifyConditions, children);
                        case OR -> buildOrConditions(simplifyConditions, operator.getChildren(), children);
                        case NOT -> buildNotConditions(simplifyConditions, children);
                    };
                }

                // AND {}, OR {}, equal true, so don't need to do anything with them
                if (result == null) {
                    if (Op.NOT.equals(operator.op())) {
                        result = DSL.falseCondition();
                    } else {
                        result = DSL.trueCondition();
                    }
                }
            }
        }

        if (result == null || (simplifyConditions && isTrue(result))) {
            result = DSL.trueCondition();
        }
        LOGGER.debug("Converted expressionItem {} into condition {}", item, result);
        return result;
    }

    private Condition buildNotConditions(final boolean simplifyConditions,
                                         final Collection<Condition> children) {
        LOGGER.debug("buildNotConditions({}, {})", simplifyConditions, children);

        final Collection<Condition> conditions;
        conditions = children
                .stream()
                .map(childCondition -> {
                    if (simplifyConditions && isFalse(childCondition)) {
                        // Not(false) == true
                        return DSL.trueCondition();
                    } else if (simplifyConditions && isTrue(childCondition)) {
                        // NOT(true) == false
                        return DSL.falseCondition();
                    } else {
                        return DSL.not(childCondition);
                    }
                })
                .collect(Collectors.toList());
        // Stroom allows NOT {} expressions to have more than one child and so
        // NOT { x=1, y=1 }
        // is treated like an implicit AND, i.e.
        // AND { NOT {x=1}, NOT {y=1}
        Condition result = DSL.and(conditions);
        LOGGER.debug("Returning {}", result);
        return result;
    }

    private Condition buildOrConditions(final boolean simplifyConditions,
                                        final List<ExpressionItem> expressionItems,
                                        final Collection<Condition> children) {
        LOGGER.debug("buildOrConditions({}, {}, {})", simplifyConditions, expressionItems, children);
        final Condition result;

        if (children.isEmpty()) {
            result = DSL.trueCondition();
        } else if (simplifyConditions
                && (children.contains(DSL.trueCondition()) || children.size() < expressionItems.size())) {
            // Either one of the OR items is TRUE so the whole is TRUE or we have less children than
            // original expressionItems so one must have been simplified to true
            // TODO There ought to be a neater way to work this out
            LOGGER.debug("One of the conditions in the OR is 1=1 so " +
                    "just return a true condition");
            result = DSL.trueCondition();
        } else if (children.stream().allMatch(this::isFalse)) {
            result = DSL.falseCondition();
        } else if (children.size() == 1) {
            // Don't wrap with the OR condition as it is not needed
            result = children.iterator().next();
        } else {
            result = or(children);
        }
        LOGGER.debug("Returning {}", result);
        return result;
    }

    private Condition buildAndConditions(final boolean simplifyConditions,
                                         final Collection<Condition> children) {
        LOGGER.debug("buildAndConditions({}, {})", simplifyConditions, children);
        final Condition result;

        if (children.isEmpty()) {
            result = DSL.trueCondition();
        } else if (simplifyConditions && children.contains(DSL.falseCondition())) {
            LOGGER.debug("One of the conditions in the AND is 1=0 " +
                    "so just return false condition");
            result = DSL.falseCondition();
        } else if (simplifyConditions && children.stream().allMatch(this::isTrue)) {
            result = DSL.trueCondition();
        } else if (children.size() == 1) {
            result = children.iterator().next();
        } else {
            result = and(children);
        }
        LOGGER.debug("Returning {}", result);
        return result;
    }

    private boolean isTrue(final Condition condition) {
        // Treat noCondition as true
        return DSL.trueCondition().equals(condition) || DSL.noCondition().equals(condition);
    }

    private boolean isFalse(final Condition condition) {
        return DSL.falseCondition().equals(condition);
    }
}
