package stroom.db.util;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DocRefField;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.or;

public class ExpressionMapper implements Function<ExpressionItem, Condition> {
    private final Map<String, TermHandler<?>> termHandlers = new HashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;

    ExpressionMapper(final WordListProvider wordListProvider,
                     final CollectionService collectionService) {
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
    }

    public <T> void map(final AbstractField dataSourceField, final Field<T> field, final TypeConverter<T> converter) {
        termHandlers.put(dataSourceField.getName(), new TermHandler<>(dataSourceField, field, converter, wordListProvider, collectionService));
    }

    public <T> void map(final AbstractField dataSourceField, final Field<T> field, final TypeConverter<T> converter, final boolean useName) {
        termHandlers.put(dataSourceField.getName(), new TermHandler<>(dataSourceField, field, converter, wordListProvider, collectionService, useName));
    }

    public void ignoreField(final AbstractField dataSourceField) {
        ignoredFields.add(dataSourceField.getName());
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
                if (ignoredFields.contains(term.getField())) {
                    return null;
                }
                throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
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

//    interface TermHandler<T> {
//        Condition apply(ExpressionTerm term);
//    }

    public interface TypeConverter<T> {
        T apply(String value);
    }

    public static class TermHandler<T> {
        private final AbstractField dataSourceField;
        private final Field<T> field;
        private final TypeConverter<T> converter;
        private final WordListProvider wordListProvider;
        private final CollectionService collectionService;
        private final boolean useName;

        public TermHandler(final AbstractField dataSourceField,
                           final Field<T> field,
                           final TypeConverter<T> converter,
                           final WordListProvider wordListProvider,
                           final CollectionService collectionService) {
            this(dataSourceField, field, converter, wordListProvider, collectionService, false);
        }

        public TermHandler(final AbstractField dataSourceField,
                           final Field<T> field,
                           final TypeConverter<T> converter,
                           final WordListProvider wordListProvider,
                           final CollectionService collectionService,
                           final boolean useName) {
            this.dataSourceField = dataSourceField;
            this.field = field;
            this.converter = converter;
            this.wordListProvider = wordListProvider;
            this.collectionService = collectionService;
            this.useName = useName;
        }

        public Condition apply(final ExpressionTerm term) {
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
                case IN_DICTIONARY: {
                    return isInDictionary(term.getDocRef());
                }
                case IN_FOLDER: {
                    return isInFolder(term.getDocRef());
                }
                case IS_DOC_REF: {
                    if (term.getDocRef() == null || term.getDocRef().getUuid() == null) {
                        return field.isNull();
                    } else {
                        return field.equal(converter.apply(term.getDocRef().getUuid()));
                    }
                }
                case IS_NULL: {
                    return field.isNull();
                }
                case IS_NOT_NULL: {
                    return field.isNotNull();
                }

                default:
                    throw new RuntimeException("Unexpected condition: " + term.getCondition());
            }

            throw new RuntimeException("Unexpected condition: " + term.getCondition());
        }

        private Condition isInDictionary(final DocRef docRef) {
            final String[] lines = loadWords(docRef);
            if (lines != null) {
                final List<T> values = Arrays.stream(lines).map(converter::apply).collect(Collectors.toList());
                return field.in(values);

//                for (final String line : lines) {
//                    if (field.getType().isNumeric()) {
//                        if (isNumericIn(fieldName, line, attribute)) {
//                            return true;
//                        }
//                    } else if (DataSourceFieldType.DATE_FIELD.equals(field.getType())) {
//                        if (isDateIn(fieldName, line, attribute)) {
//                            return true;
//                        }
//                    } else {
//                        if (isIn(line)) {
//                            return true;
//                        }
//                    }
//                }
            }

            return null;
        }

        private Condition isInFolder(final DocRef docRef) {
            Condition condition = null;

            if (dataSourceField instanceof DocRefField) {
                final String type = ((DocRefField) dataSourceField).getDocRefType();
                if (type != null && collectionService != null) {
                    final Set<DocRef> descendants = collectionService.getDescendants(docRef, type);
                    if (descendants == null || descendants.size() == 0) {
                        condition = field.in(Collections.emptySet());
                    } else {
                        for (final DocRef descendant : descendants) {
                            String value = descendant.getUuid();
                            if (useName) {
                                value = descendant.getName();
                            }

                            if (condition == null) {
                                condition = field.equal(converter.apply(value));
                            } else {
                                condition = condition.or(field.equal(converter.apply(value)));
                            }
                        }
                    }
                }
            }

            return condition;
        }

        private String[] loadWords(final DocRef docRef) {
            if (wordListProvider == null) {
                return null;
            }
            return wordListProvider.getWords(docRef);
        }
//
//        private boolean isNumericIn(final Object termValue, final Object attribute) {
//            final long num = getNumber(fieldName, attribute);
//            final long[] in = getNumbers(fieldName, termValue);
//            if (in != null) {
//                for (final long n : in) {
//                    if (n == num) {
//                        return true;
//                    }
//                }
//            }
//
//            return false;
//        }
//
//        private boolean isDateIn(final String fieldName, final Object termValue, final Object attribute) {
//            final long num = getDate(fieldName, attribute);
//            final long[] in = getDates(fieldName, termValue);
//            if (in != null) {
//                for (final long n : in) {
//                    if (n == num) {
//                        return true;
//                    }
//                }
//            }
//
//            return false;
//        }
//
//        private Condition isIn(final Object value) {
//            final String[] termValues = value.toString().split(" ");
//            final List<T> values = Arrays.stream(termValues).map(converter::apply).collect(Collectors.toList());
//            return field.in(values);
//        }
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
