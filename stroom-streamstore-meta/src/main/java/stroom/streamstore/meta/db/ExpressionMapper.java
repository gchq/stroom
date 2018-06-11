package stroom.streamstore.meta.db;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.or;

class ExpressionMapper implements Function<ExpressionItem, Condition> {
    private final Map<String, TermHandler<?>> termHandlers;

    ExpressionMapper(final Map<String, TermHandler<?>> termHandlers) {
        this.termHandlers = termHandlers;
    }

    @Override
    public Condition apply(final ExpressionItem item) {
        if (item == null || !item.enabled()) {
            return null;
        }

        if (item instanceof ExpressionTerm) {
            final ExpressionTerm term = (ExpressionTerm) item;
            final TermHandler<?> termHandler = termHandlers.get(term.getField());
            if (termHandler == null) {
                throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
            }

            return termHandler.apply(term);

        } else if (item instanceof ExpressionOperator) {
            final ExpressionOperator operator = (ExpressionOperator) item;

            final Condition[] children = operator.getChildren().stream()
                    .map(this)
                    .filter(Objects::nonNull)
                    .toArray(Condition[]::new);

            switch (operator.getOp()) {
                case AND:
                    return and(children);
                case OR:
                    return or(children);
                case NOT:

                    if (children.length == 1) {
                        // A single child, just apply the 'not' to that first item
                        return not(children[0]);
                    } else if (children.length > 1) {
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

        return null;
    }

//    interface TermHandler<T> {
//        Condition apply(ExpressionTerm term);
//    }

    interface TypeConverter<T> {
        T apply(String value);
    }

    static class TermHandler<T> {
        private final Field<T> field;
        private final TypeConverter<T> converter;

        TermHandler(final Field<T> field,
                        final TypeConverter<T> converter) {
            this.field = field;
            this.converter = converter;
        }

        Condition apply(final ExpressionTerm term) {
            switch (term.getCondition()) {
                case EQUALS: {
                    return field.equal(converter.apply(term.getValue()));
                }
                case CONTAINS: {
                    return field.like("%" + converter.apply(term.getValue()) + "%");
                }
                case BETWEEN: {
                    final String[] parts = term.getValue().split(",");
                    if (parts.length == 2) {
                        return field.between(converter.apply(parts[0]), converter.apply(parts[1]));
                    }
                    break;
                }
                case GREATER_THAN: {
                    return field.greaterThan(converter.apply(term.getValue()));
                }
                case GREATER_THAN_OR_EQUAL_TO: {
                    return field.greaterOrEqual(converter.apply(term.getValue()));
                }
                case LESS_THAN: {
                    return field.lessThan(converter.apply(term.getValue()));
                }
                case LESS_THAN_OR_EQUAL_TO: {
                    return field.lessOrEqual(converter.apply(term.getValue()));
                }
                case IN: {
                    final String[] parts = term.getValue().split(",");
                    final List<T> values = Arrays.stream(parts).map(converter::apply).collect(Collectors.toList());
                    return field.in(values);
                }
//                case IN_DICTIONARY: {
//                    // TODO : @66 Not sure how to handle this yet
//                }
                default:
                    throw new RuntimeException("Unexpected condition: " + term.getCondition());
            }

            throw new RuntimeException("Unexpected condition: " + term.getCondition());
        }
    }
//
//    private class LongTermHandler implements TermHandler<Long> {
//        private final Field<Long> field;
//
//        LongTermHandler(final Field<Long> field) {
//            this.field = field;
//        }
//
//        @Override
//        public Condition apply(final ExpressionTerm term) {
//            switch (term.getCondition()) {
//            case EQUALS: {
//                return field.equal(Long.valueOf(term.getValue()));
//            }
//            case CONTAINS: {
//                return field.like("%" + term.getValue() + "%");
//            }
//            case BETWEEN: {
//                final String[] parts = term.getValue().split(",");
//                if (parts.length == 2) {
//                    return field.between(Long.valueOf(parts[0]), Long.valueOf(parts[1]));
//                }
//                break;
//            }
//            case GREATER_THAN: {
//                return field.greaterThan(Long.valueOf(term.getValue()));
//            }
//            case GREATER_THAN_OR_EQUAL_TO: {
//                return field.greaterOrEqual(Long.valueOf(term.getValue()));
//            }
//            case LESS_THAN: {
//                return field.lessThan(Long.valueOf(term.getValue()));
//            }
//            case LESS_THAN_OR_EQUAL_TO: {
//                return field.lessOrEqual(Long.valueOf(term.getValue()));
//            }
//            case IN: {
//                final String[] parts = term.getValue().split(",");
//                final Long[] values = Arrays.stream(parts).map(Long::valueOf).toArray(Long[]::new);
//                return field.in(values);
//            }
//            case IN_DICTIONARY: {
//                // Not sure how to handle this yet
//            }
//        }
//    }
}
