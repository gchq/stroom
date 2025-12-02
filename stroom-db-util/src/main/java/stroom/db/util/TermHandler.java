package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.Provider;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class TermHandler<T> implements Function<ExpressionTerm, Condition> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TermHandler.class);

    private static final String LIST_DELIMITER = ",";
    private static final String ASTERISK = "*";
    private static final String PERCENT = "%";
    private static final Pattern ASTERISK_PATTERN = Pattern.compile("\\*");

    private final QueryField dataSourceField;
    private final Field<T> field;
    private final ExpressionMapper.MultiConverter<T> converter;
    private final Provider<WordListProvider> wordListProvider;
    private final Provider<CollectionService> collectionServiceProvider;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    private final boolean useName;
    private final boolean fieldIsCaseSensitive;

    TermHandler(final QueryField dataSourceField,
                final Field<T> field,
                final ExpressionMapper.MultiConverter<T> converter,
                final Provider<WordListProvider> wordListProvider,
                final Provider<CollectionService> collectionServiceProvider,
                final Provider<DocRefInfoService> docRefInfoServiceProvider,
                final boolean useName,
                final boolean fieldIsCaseSensitive) {
        this.dataSourceField = dataSourceField;
        this.field = field;
        this.converter = converter;
        this.wordListProvider = wordListProvider;
        this.collectionServiceProvider = collectionServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.useName = useName;
        this.fieldIsCaseSensitive = fieldIsCaseSensitive;
    }

    @Override
    public Condition apply(final ExpressionTerm term) {
        switch (term.getCondition()) {
            case EQUALS -> {
                // TODO : Currently equality is used for wild carding with `*` but should probably use the
                //  `MATCHES_REGEX` condition.
                //  Also `is null` is being assumed if the value is null when we probably want to change this to
                //  use the `IS_NULL` condition. Keeping this the same for now to reduce the change of breaking
                //  backward compatibility.
                return eq(term);

//                if (fieldIsCaseSensitive) {
//                    if (term.getValue() != null) {
//                        return field.equalIgnoreCase(term.getValue());
//                    }
//                } else {
//                    return getCondition(term, field::eq);
//                }
            }
            case NOT_EQUALS -> {
                return neq(term);
            }
            case EQUALS_CASE_SENSITIVE -> {
                return getCondition(term, field::eq);
            }
            case NOT_EQUALS_CASE_SENSITIVE -> {
                return getCondition(term, field::ne);
            }
            case CONTAINS -> {
                if (fieldIsCaseSensitive) {
                    return getCondition(term, field::containsIgnoreCase);
                } else {
                    return getCondition(term, field::contains);
                }
            }
            case CONTAINS_CASE_SENSITIVE -> {
                return getCondition(term, field::contains);
            }
            case STARTS_WITH -> {
                if (fieldIsCaseSensitive) {
                    return getCondition(term, field::startsWithIgnoreCase);
                } else {
                    return getCondition(term, field::startsWith);
                }
            }
            case STARTS_WITH_CASE_SENSITIVE -> {
                return getCondition(term, field::startsWith);
            }
            case ENDS_WITH -> {
                if (fieldIsCaseSensitive) {
                    return getCondition(term, field::endsWithIgnoreCase);
                } else {
                    return getCondition(term, field::endsWith);
                }
            }
            case ENDS_WITH_CASE_SENSITIVE -> {
                return getCondition(term, field::endsWith);
            }
            case BETWEEN -> {
                final String[] parts = term.getValue().split(LIST_DELIMITER);
                if (parts.length == 2) {
                    final List<T> value1 = getValues(parts[0]);
                    final List<T> value2 = getValues(parts[1]);
                    if (value1.size() == 1 && value2.size() == 1) {
                        return field.between(value1.getFirst(), value2.getFirst());
                    }
                }
            }
            case GREATER_THAN -> {
                return getSingleValue(term.getValue()).map(field::greaterThan).orElse(DSL.falseCondition());
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                return getSingleValue(term.getValue()).map(field::greaterOrEqual).orElse(DSL.falseCondition());
            }
            case LESS_THAN -> {
                return getSingleValue(term.getValue()).map(field::lessThan).orElse(DSL.falseCondition());
            }
            case LESS_THAN_OR_EQUAL_TO -> {
                return getSingleValue(term.getValue()).map(field::lessOrEqual).orElse(DSL.falseCondition());
            }
            case IN -> {
                final String value = NullSafe.get(term.getValue(), String::trim);
                if (value != null && !value.isEmpty()) {
                    final String[] parts = value.split(LIST_DELIMITER);
                    final List<String> partsList = Arrays.stream(parts)
                            .map(String::trim)
                            .filter(part -> !part.isEmpty())
                            .toList();
                    final List<T> values = getValues(partsList);
                    return field.in(values);
                } else {
                    // Empty in list so same as 1==0
                    return DSL.falseCondition();
                }
            }
            case IN_DICTIONARY -> {
                return isInDictionary(term.getDocRef());
            }
            case IN_FOLDER -> {
                return isInFolder(term, term.getDocRef());
            }
            case IS_DOC_REF -> {
                if (term.getDocRef() == null || term.getDocRef().getUuid() == null) {
                    return field.isNull();
                } else {
                    final String docValue = getDocValue(term, term.getDocRef());
                    final List<T> value = getValues(docValue);
                    // IS_DOC_REF does not support wild carding so should only get one thing back
                    // else fall through and match nothing
                    if (value.size() == 1) {
                        return field.equal(value.getFirst());
                    }
                }
            }
            case IS_USER_REF -> {
                if (term.getDocRef() == null || term.getDocRef().getUuid() == null) {
                    return field.isNull();
                } else {
                    final String docValue = getDocValue(term, term.getDocRef());
                    final List<T> value = getValues(docValue);
                    // IS_DOC_REF does not support wild carding so should only get one thing back
                    // else fall through and match nothing
                    if (value.size() == 1) {
                        return field.equal(value.getFirst());
                    }
                }
            }
            case IS_NULL -> {
                return field.isNull();
            }
            case IS_NOT_NULL -> {
                return field.isNotNull();
            }
            case MATCHES_REGEX -> {
                return field.likeRegex(term.getValue());
            }
            default -> throw new RuntimeException("Unexpected condition '" +
                                                  term.getCondition() +
                                                  "' for term: " +
                                                  term);
        }

        return DSL.falseCondition();
    }

    /**
     * Get an identifier for the passed docRef. term is only used for logging.
     * docRef may be the same as term.getDocRef() or a descendant of it, as in 'in folder'.
     *
     * @return The name of the doc or its uuid depending on how the {@link TermHandler} is
     * configured.
     */
    private String getDocValue(final ExpressionTerm term, final DocRef docRef) {
        if (useName) {
            if (docRefInfoServiceProvider != null) {
                final Optional<String> resolvedName = docRefInfoServiceProvider.get().name(docRef);
                if (resolvedName.isEmpty()) {
                    throw new RuntimeException("Unable to find doc with reference '" +
                                               docRef +
                                               "' for term: " +
                                               term.toString());
                }
                return resolvedName.get();
            }
            return docRef.getName();
        } else {
            return docRef.getUuid();
        }
    }

    private Condition eq(final ExpressionTerm term) {
        final List<T> list = getValues(term.getValue());
        if (NullSafe.isEmptyCollection(list)) {
            return field.isNull();
        } else if (list.size() > 1) {
            return field.in(list);
        } else {
            final T t = list.getFirst();
            if (t instanceof final String string) {
                if (string.contains(ASTERISK)) {
                    final String like = ASTERISK_PATTERN.matcher(string).replaceAll(PERCENT);
                    return field.like(like);
                }
            }
            return field.equal(t);
        }
    }

    private Condition neq(final ExpressionTerm term) {
        final List<T> list = getValues(term.getValue());
        if (NullSafe.isEmptyCollection(list)) {
            return field.isNotNull();
        } else if (list.size() > 1) {
            return field.notIn(list);
        } else {
            final T t = list.getFirst();
            if (t instanceof final String string) {
                if (string.contains(ASTERISK)) {
                    final String like = ASTERISK_PATTERN.matcher(string).replaceAll(PERCENT);
                    return field.notLike(like);
                }
            }
            return field.notEqual(t);
        }
    }

    private Condition getCondition(final ExpressionTerm term,
                                   final Function<T, Condition> function) {
        return getSingleValue(term.getValue()).map(function).orElse(DSL.falseCondition());
    }

    private Optional<T> getSingleValue(final String value) {
        final List<T> values = converter.apply(NullSafe.singletonList(value));
        if (values.size() == 1) {
            return Optional.of(values.getFirst());
        }
        return Optional.empty();
    }

    private List<T> getValues(final String value) {
        return converter.apply(NullSafe.singletonList(value));
    }

    private List<T> getValues(final List<String> values) {
        return converter.apply(values);
    }

    private Condition isInDictionary(final DocRef docRef) {
        final String[] lines = loadWords(docRef);
        if (lines != null) {
            final List<T> values = converter.apply(List.of(lines));
            LOGGER.debug(() -> LogUtil.message("Converted {} lines into {} values",
                    lines.length, values.size()));
            return field.in(values);
        }
        return field.in(Collections.emptyList());
    }

    private Condition isInFolder(final ExpressionTerm term, final DocRef docRef) {
        Condition condition = field.in(Collections.emptyList());

        if (FieldType.DOC_REF.equals(dataSourceField.getFldType())) {
            final String type = dataSourceField.getDocRefType();
            if (type != null && collectionServiceProvider != null) {
                final Set<DocRef> descendants = collectionServiceProvider.get().getDescendants(docRef, type);
                if (descendants != null && !descendants.isEmpty()) {
                    final List<String> values = descendants.stream()
                            .map(descendant ->
                                    getDocValue(term, descendant))
                            .toList();
                    final Set<T> set = new HashSet<>(converter.apply(values));
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
        return wordListProvider.get().getWords(docRef);
    }
}
