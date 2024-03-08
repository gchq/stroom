package stroom.datasource.api.v2;

import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public enum ConditionSet {
    DEFAULT_NUMERIC(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.BETWEEN,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO),
    DEFAULT_ID(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY,
            Condition.BETWEEN,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO),
    DEFAULT_BOOLEAN(
            Condition.EQUALS,
            Condition.NOT_EQUALS),
    DEFAULT_DATE(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.BETWEEN,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO),
    DEFAULT_KEYWORD(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),
    DOC_REF_UUID(
            Condition.IS_DOC_REF,
            Condition.IN_FOLDER),
    DOC_REF_NAME(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),
    DOC_REF_ALL(
            Condition.IS_DOC_REF,
            Condition.IN_FOLDER,
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),
    DEFAULT_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),

    // Elastic Conditions.
    ELASTIC_NUMERIC(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY),
    ELASTIC_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY,
            Condition.MATCHES_REGEX),

    // Solr Conditions.
    SOLR_ID(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),
    SOLR_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),
    SOLR_DATE(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY),
    SOLR_BOOLEAN(
            Condition.EQUALS,
            Condition.NOT_EQUALS),
    SOLR_NUMERIC(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY),

    // Reference Data Conditions.
    REF_DATA_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),
    REF_DATA_DOC_REF(
            Condition.IS_DOC_REF,
            Condition.EQUALS,
            Condition.NOT_EQUALS),

    // Statistic Conditions.
    STAT_DATE(
            Condition.BETWEEN),
    STAT_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN),

    // Basic.
    BASIC_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN),

    // UI Defaults.
    UI_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY),
    UI_DOC_REF(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY,
            Condition.IS_DOC_REF),
    UI_NUMERIC(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY),
    UI_DATE(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY);


    private final List<Condition> conditionList;
    private final Set<Condition> conditionSet;

    public static ConditionSet getDefault(final FieldType fieldType) {
        switch (fieldType) {
            case ID: {
                return ConditionSet.DEFAULT_ID;
            }
            case BOOLEAN: {
                return ConditionSet.DEFAULT_BOOLEAN;
            }
            case INTEGER: {
                return ConditionSet.DEFAULT_NUMERIC;
            }
            case LONG: {
                return ConditionSet.DEFAULT_NUMERIC;
            }
            case FLOAT: {
                return ConditionSet.DEFAULT_NUMERIC;
            }
            case DOUBLE: {
                return ConditionSet.DEFAULT_NUMERIC;
            }
            case DATE: {
                return ConditionSet.DEFAULT_DATE;
            }
            case TEXT: {
                return ConditionSet.DEFAULT_TEXT;
            }
            case KEYWORD: {
                return ConditionSet.DEFAULT_KEYWORD;
            }
            case IPV4_ADDRESS: {
                return ConditionSet.DEFAULT_NUMERIC;
            }
            case DOC_REF: {
                return ConditionSet.DOC_REF_ALL;
            }
        }
        throw new RuntimeException("Unknown field type");
    }

    public static ConditionSet getUiDefaultConditions(final FieldType fieldType) {
        if (fieldType != null) {
            if (FieldType.DOC_REF.equals(fieldType)) {
                return UI_DOC_REF;
            } else if (fieldType.isNumeric()) {
                return UI_NUMERIC;

            } else if (FieldType.DATE.equals(fieldType)) {
                return UI_DATE;
            }
        }
        return UI_TEXT;
    }

    ConditionSet(Condition... arr) {
        conditionList = Arrays.asList(arr);
        conditionSet = new HashSet<>(conditionList);
    }

    public boolean supportsCondition(final Condition condition) {
        Objects.requireNonNull(condition);
        return conditionSet.contains(condition);
    }

    public List<Condition> getConditionList() {
        return conditionList;
    }

    public String toString() {
        return conditionList.stream()
                .map(Condition::getDisplayValue)
                .map(str -> "'" + str + "'")
                .collect(Collectors.joining(", "));
    }
}
