package stroom.streamstore.meta.impl.db;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.meta.impl.db.ExpressionMapper.TermHandler;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.or;

class MetaExpressionMapper implements Function<ExpressionItem, Condition> {
    private final Map<String, MetaTermHandler> termHandlers;

    MetaExpressionMapper(final Map<String, MetaTermHandler> termHandlers) {
        this.termHandlers = termHandlers;
    }

    @Override
    public Condition apply(final ExpressionItem item) {
        if (item == null || !item.enabled()) {
            return null;
        }

        if (item instanceof ExpressionTerm) {
            final ExpressionTerm term = (ExpressionTerm) item;
            final MetaTermHandler termHandler = termHandlers.get(term.getField());
            if (termHandler == null) {
//                throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
                return null;
            }

            return termHandler.apply(term);

        } else if (item instanceof ExpressionOperator) {
            final ExpressionOperator operator = (ExpressionOperator) item;

            final Condition[] children = operator.getChildren().stream()
                    .map(this)
                    .filter(Objects::nonNull)
                    .toArray(Condition[]::new);

            if (children.length > 0) {
                switch (operator.getOp()) {
                    case AND:
                        return and(children);
                    case OR:
                        return or(children);
                    case NOT:

                        if (children.length == 1) {
                            // A single child, just apply the 'not' to that first item
                            return not(children[0]);
                        } else {
                            // If there are multiple children, apply an 'and' around them all
                            return and(Arrays.stream(children)
                                    .map(DSL::not)
                                    .toArray(Condition[]::new));
                        }
                    default:
                        // Fall through to null if there aren't any children
                        break;
                }
            }
        }

        return null;
    }

    static class MetaTermHandler {
        private final Field<Integer> keyField;
        private final int keyId;
        private final TermHandler<Long> valueHandler;

        MetaTermHandler(final Field<Integer> keyField, final int keyId, final TermHandler<Long> valueHandler) {
            this.keyField = keyField;
            this.valueHandler = valueHandler;
            this.keyId = keyId;
        }

        Condition apply(final ExpressionTerm term) {
            return keyField.equal(keyId).and(valueHandler.apply(term));
        }
    }
}
