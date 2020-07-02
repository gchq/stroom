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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.or;

public final class CommonExpressionMapper implements Function<ExpressionItem, Condition> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonExpressionMapper.class);

    private static final String LIST_DELIMITER = ",";
    private static final String ASTERISK = "*";
    private static final String PERCENT = "%";
    private static final Pattern ASTERISK_PATTERN = Pattern.compile("\\*");

    private final Map<String, Function<ExpressionTerm, Condition>> termHandlers = new HashMap<>();
    private final Set<String> ignoredFields = new HashSet<>();
    private final Function<ExpressionItem, Condition> delegateItemHandler;

    public CommonExpressionMapper(){
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
        final Condition conditions = apply(item, true);
        return conditions;
    }

    public Condition apply(final ExpressionItem item,
                           final boolean simplifyConditions) {
        Condition result = null;

        if (item != null && item.enabled()) {
            if (item instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) item;
                final Function<ExpressionTerm, Condition> termHandler = termHandlers.get(term.getField());
                if (termHandler != null) {
                    result = termHandler.apply(term);

                } else if (delegateItemHandler != null){
                    result = delegateItemHandler.apply(term);
                }
                else if (!ignoredFields.contains(term.getField())) {
                    throw new RuntimeException("No term handler supplied for term '" + term.getField() + "'");
                }

            } else if (item instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) item;
                if (operator.getChildren() != null && !operator.getChildren().isEmpty()) {
                    final Collection<Condition> children = operator.getChildren()
                            .stream()
                            .map(expressionItem -> apply(expressionItem, simplifyConditions))
                            .collect(Collectors.toList());

                    switch (operator.op()) {
                        case AND:
                            result = buildAndConditions(simplifyConditions, children);
                            break;
                        case OR:
                            result = buildOrConditions(simplifyConditions, operator.getChildren(), children);
                            break;
                        case NOT:
                            result = buildNotConditions(simplifyConditions, children);
                    }
                }

                // AND {}, OR {}, equal true, so don't need to do anything with them
                if (result == null) {
                    if (ExpressionOperator.Op.NOT.equals(operator.op())) {
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
                    final String[] parts = term.getValue().split(LIST_DELIMITER);
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
                    List<T> values = Collections.emptyList();
                    final String value = term.getValue().trim();
                    if (value.length() > 0) {
                        final String[] parts = value.split(LIST_DELIMITER);
                        values = Arrays.stream(parts)
                                .map(String::trim)
                                .filter(part -> part.length() > 0)
                                .map(this::getValues)
                                .flatMap(List::stream)
                                .collect(Collectors.toList());
                    }
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
                        if (string.contains(ASTERISK)) {
                            final String like = ASTERISK_PATTERN.matcher(string).replaceAll(PERCENT);
                            return field.like(like);
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
