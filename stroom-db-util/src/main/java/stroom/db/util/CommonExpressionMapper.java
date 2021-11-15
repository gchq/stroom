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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        final Optional<Condition> optional = innerApply(item);
        LOGGER.debug(() -> optional.map(Object::toString).orElse(null));
        return optional.orElse(DSL.noCondition());
    }

    public Optional<Condition> innerApply(final ExpressionItem item) {
        Optional<Condition> result = Optional.empty();

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
                    result = Optional.of(termHandler.apply(term));
                } else if (delegateItemHandler != null) {
                    result = Optional.of(delegateItemHandler.apply(term));
                } else if (!ignoredFields.contains(term.getField())) {
                    throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
                }

            } else if (item instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) item;
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
                    final Condition child = children.get(0);
                    if (Op.NOT.equals(operator.op())) {
                        result = Optional.of(DSL.not(child));
                    } else {
                        result = Optional.of(child);
                    }

                } else if (children.size() > 0) {
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
