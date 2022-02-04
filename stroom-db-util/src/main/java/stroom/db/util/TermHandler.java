package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DocRefField;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.api.v2.ExpressionTerm;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TermHandler<T> implements Function<ExpressionTerm, Condition> {

    private static final String LIST_DELIMITER = ",";
    private static final String ASTERISK = "*";
    private static final String PERCENT = "%";
    private static final Pattern ASTERISK_PATTERN = Pattern.compile("\\*");

    private final AbstractField dataSourceField;
    private final Field<T> field;
    private final ExpressionMapper.MultiConverter<T> converter;
    private final WordListProvider wordListProvider;
    private final CollectionService collectionService;
    private final DocRefInfoService docRefInfoService;
    private final boolean useName;

    TermHandler(final AbstractField dataSourceField,
                final Field<T> field,
                final ExpressionMapper.MultiConverter<T> converter,
                final WordListProvider wordListProvider,
                final CollectionService collectionService,
                final DocRefInfoService docRefInfoService,
                final boolean useName) {
        this.dataSourceField = dataSourceField;
        this.field = field;
        this.converter = converter;
        this.wordListProvider = wordListProvider;
        this.collectionService = collectionService;
        this.docRefInfoService = docRefInfoService;
        this.useName = useName;
    }

    @Override
    public Condition apply(final ExpressionTerm term) {
        switch (term.getCondition()) {
            case EQUALS, CONTAINS -> {
                return eq(term);
            }
            case BETWEEN -> {
                final String[] parts = term.getValue().split(LIST_DELIMITER);
                if (parts.length == 2) {
                    final List<T> value1 = getValues(parts[0]);
                    final List<T> value2 = getValues(parts[1]);
                    if (value1.size() == 1 && value2.size() == 1) {
                        return field.between(value1.get(0), value2.get(0));
                    }
                }
            }
            case GREATER_THAN -> {
                final List<T> value = getValues(term.getValue());
                if (value.size() == 1) {
                    return field.greaterThan(value.get(0));
                }
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                final List<T> value = getValues(term.getValue());
                if (value.size() == 1) {
                    return field.greaterOrEqual(value.get(0));
                }
            }
            case LESS_THAN -> {
                final List<T> value = getValues(term.getValue());
                if (value.size() == 1) {
                    return field.lessThan(value.get(0));
                }
            }
            case LESS_THAN_OR_EQUAL_TO -> {
                final List<T> value = getValues(term.getValue());
                if (value.size() == 1) {
                    return field.lessOrEqual(value.get(0));
                }
            }
            case IN -> {
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
                    if (value.size() == 1) {
                        return field.equal(value.get(0));
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

        return field.in(Collections.emptyList());
    }

    private String getDocValue(final ExpressionTerm term, final DocRef docRef) {
        if (useName) {
            if (docRefInfoService != null) {
                final Optional<String> resolvedName = docRefInfoService.name(docRef);
                if (resolvedName.isEmpty()) {
                    throw new RuntimeException("Unable to find doc with reference '" +
                            docRef +
                            "' for term: " +
                            term.toString());
                }
                return resolvedName.get();
            }
            return docRef.getName();
        }
        return docRef.getUuid();
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
        }
        return field.in(list);
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
        return field.in(Collections.emptyList());
    }

    private Condition isInFolder(final ExpressionTerm term, final DocRef docRef) {
        Condition condition = field.in(Collections.emptyList());

        if (dataSourceField instanceof DocRefField) {
            final String type = ((DocRefField) dataSourceField).getDocRefType();
            if (type != null && collectionService != null) {
                final Set<DocRef> descendants = collectionService.getDescendants(docRef, type);
                if (descendants != null && descendants.size() > 0) {
                    final Set<T> set = new HashSet<>();
                    for (final DocRef descendant : descendants) {
                        final String value = getDocValue(term, descendant);
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
