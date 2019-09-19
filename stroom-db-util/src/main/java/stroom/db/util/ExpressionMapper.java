package stroom.db.util;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import stroom.datasource.api.v2.DataSourceField;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.server.CollectionService;
import stroom.streamstore.server.WordListProvider;

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

    public <T> void map(final DataSourceField dataSourceField, final Field<T> field, final Converter<T> converter) {
        termHandlers.put(dataSourceField.getName(), new TermHandler<>(dataSourceField, field, new ConverterAdapter<>(converter), wordListProvider, collectionService));
    }

    public <T> void multiMap(final DataSourceField dataSourceField, final Field<T> field, final MultiConverter<T> converter) {
        termHandlers.put(dataSourceField.getName(), new TermHandler<>(dataSourceField, field, converter, wordListProvider, collectionService));
    }

    public <T> void multiMap(final DataSourceField dataSourceField, final Field<T> field, final MultiConverter<T> converter, final boolean useName) {
        termHandlers.put(dataSourceField.getName(), new TermHandler<>(dataSourceField, field, converter, wordListProvider, collectionService, useName));
    }

    public void ignoreField(final DataSourceField dataSourceField) {
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

    public interface Converter<T> {
        T apply(String value);
    }

    public interface MultiConverter<T> {
        List<T> apply(String value);
    }

    private static class ConverterAdapter<T> implements MultiConverter<T> {
        private final Converter<T> converter;

        ConverterAdapter(final Converter<T> converter) {
            this.converter = converter;
        }

        @Override
        public List<T> apply(final String value) {
            final T t = converter.apply(value);
            if (t != null) {
                return Collections.singletonList(t);
            }
            return Collections.emptyList();
        }
    }

    public static class TermHandler<T> {
        private final DataSourceField dataSourceField;
        private final Field<T> field;
        private final MultiConverter<T> converter;
        private final WordListProvider wordListProvider;
        private final CollectionService collectionService;
        private final boolean useName;

        public TermHandler(final DataSourceField dataSourceField,
                           final Field<T> field,
                           final MultiConverter<T> converter,
                           final WordListProvider wordListProvider,
                           final CollectionService collectionService) {
            this(dataSourceField, field, converter, wordListProvider, collectionService, false);
        }

        TermHandler(final DataSourceField dataSourceField,
                    final Field<T> field,
                    final MultiConverter<T> converter,
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
                    return eq(term);
                }
                case CONTAINS: {
                    return eq(term);
                }
                case BETWEEN: {
                    final String[] parts = term.getValue().split(",");
                    if (parts.length == 2) {
                        return field.between(getSingleValue(parts[0]), getSingleValue(parts[1]));
                    }
                    break;
                }
                case GREATER_THAN: {
                    return field.greaterThan(getSingleValue(term.getValue()));
                }
                case GREATER_THAN_OR_EQUAL_TO: {
                    return field.greaterOrEqual(getSingleValue(term.getValue()));
                }
                case LESS_THAN: {
                    return field.lessThan(getSingleValue(term.getValue()));
                }
                case LESS_THAN_OR_EQUAL_TO: {
                    return field.lessOrEqual(getSingleValue(term.getValue()));
                }
                case IN: {
                    final String[] parts = term.getValue().split(",");
                    final List<T> values = Arrays.stream(parts)
                            .map(this::getValues)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
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
                    } else if (useName) {
                        return field.equal(getSingleValue(term.getDocRef().getName()));
                    } else {
                        return field.equal(getSingleValue(term.getDocRef().getUuid()));
                    }
                }

                default:
                    throw new RuntimeException("Unexpected condition: " + term.getCondition());
            }

            throw new RuntimeException("Unexpected condition: " + term.getCondition());
        }

        private Condition eq(final ExpressionTerm term) {
            final List<T> list = getValues(term.getValue());
            if (list.size() > 0) {
                if (list.size() > 1) {
                    return field.in(list);
                } else {
                    final T t = list.get(0);
                    if (t instanceof String) {
                        final String string = (String) t;
                        if (string.contains("*")) {
                            return field.like(string.replaceAll("\\*", "%"));
                        }
                    }
                    return field.eq(t);
                }
            } else {
                return field.in(list);
            }
        }

        private T getSingleValue(final String value) {
            final List<T> list = converter.apply(value);
            if (list.size() != 1) {
                throw new RuntimeException("Expected single value");
            }
            return list.get(0);
        }

        private List<T> getValues(final String value) {
            return converter.apply(value);
        }

        private Condition isInDictionary(final DocRef docRef) {
            final String[] lines = loadWords(docRef);
            if (lines != null) {
                final List<T> values = Arrays.stream(lines).map(this::getSingleValue).collect(Collectors.toList());
                return field.in(values);
            }

            return null;
        }

        private Condition isInFolder(final DocRef docRef) {
            Condition condition = null;
//
//            if (dataSourceField.getType(). instanceof DataSourceField) {
//                final String type = ((DataSourceField) dataSourceField).getDocRefType();
//                if (type != null && collectionService != null) {
//                    final Set<DocRef> descendants = collectionService.getDescendants(docRef, type);
//                    if (descendants == null || descendants.size() == 0) {
//                        condition = field.in(Collections.emptySet());
//                    } else {
//                        for (final DocRef descendant : descendants) {
//                            String value = descendant.getUuid();
//                            if (useName) {
//                                value = descendant.getName();
//                            }
//
//                            if (condition == null) {
//                                condition = field.equal(getSingleValue(value));
//                            } else {
//                                condition = condition.or(field.equal(getSingleValue(value)));
//                            }
//                        }
//                    }
//                }
//            }

            return condition;
        }

        private String[] loadWords(final DocRef docRef) {
            if (wordListProvider == null) {
                return null;
            }
            return wordListProvider.getWords(docRef);
        }
    }
}
