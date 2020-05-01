package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DocRefField;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.or;

public final class CommonExpressionMapper implements Function<ExpressionItem, Collection<Condition>> {
    private final Map<String, Function<ExpressionTerm, Condition>> termHandlers = new HashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();
    private final boolean ignoreMissingHandler;

    public CommonExpressionMapper(final boolean ignoreMissingHandler) {
        this.ignoreMissingHandler = ignoreMissingHandler;
    }

    public void addHandler(final AbstractField dataSourceField, final Function<ExpressionTerm, Condition> handler) {
        termHandlers.put(dataSourceField.getName(), handler);
    }

    public void ignoreField(final AbstractField dataSourceField) {
        ignoredFields.add(dataSourceField.getName());
    }

    @Override
    public Collection<Condition> apply(final ExpressionItem item) {
        Collection<Condition> result = Collections.emptyList();

        if (item != null && item.isEnabled()) {
            if (item instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) item;
                final Function<ExpressionTerm, Condition> termHandler = termHandlers.get(term.getField());
                if (termHandler != null) {
                    result = Collections.singleton(termHandler.apply(term));

                } else if (!ignoreMissingHandler && !ignoredFields.contains(term.getField())) {
                    throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
                }

            } else if (item instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) item;

                final Collection<Condition> children = operator.getChildren()
                        .stream()
                        .map(this)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

                if (children.size() > 0) {
                    switch (operator.getOp()) {
                        case AND:
                            result = Collections.singleton(and(children));
                            break;
                        case OR:
                            result = Collections.singleton(or(children));
                            break;
                        case NOT:
                            result = children
                                    .stream()
                                    .map(DSL::not)
                                    .collect(Collectors.toList());
                    }
                }
            }
        }

        return result;
    }

    public static final class TermHandler<T> implements Function<ExpressionTerm, Condition> {
        private final AbstractField dataSourceField;
        private final Field<T> field;
        private final ExpressionMapper.MultiConverter<T> converter;
        private final WordListProvider wordListProvider;
        private final CollectionService collectionService;
        private final boolean useName;

        public TermHandler(final AbstractField dataSourceField,
                           final Field<T> field,
                           final ExpressionMapper.MultiConverter<T> converter,
                           final WordListProvider wordListProvider,
                           final CollectionService collectionService) {
            this(dataSourceField, field, converter, wordListProvider, collectionService, false);
        }

        TermHandler(final AbstractField dataSourceField,
                    final Field<T> field,
                    final ExpressionMapper.MultiConverter<T> converter,
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

        @Override
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
                final List<T> values = new ArrayList<>();
                for (final String line : lines) {
                    final List<T> list = converter.apply(line);
                    values.addAll(list);
                }
                return field.in(values);
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
                        final Set<T> set = new HashSet<>();
                        for (final DocRef descendant : descendants) {
                            String value = descendant.getUuid();
                            if (useName) {
                                value = descendant.getName();
                            }
                            final List<T> list = converter.apply(value);
                            set.addAll(list);
                        }
                        condition = field.in(set);
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
    }
}
