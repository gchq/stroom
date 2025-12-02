package stroom.query.api.datasource;

import stroom.query.api.ExpressionTerm.Condition;

import java.util.Arrays;
import java.util.EnumSet;
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
    DEFAULT_DENSE_VECTOR(
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
    CASE_SENSITIVE_TEXT(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.EQUALS_CASE_SENSITIVE,
            Condition.NOT_EQUALS_CASE_SENSITIVE,
            Condition.IN,
            Condition.IN_DICTIONARY),

    // Receipt Policy Rules
    /**
     * Conditions that support obfuscation/hashing of the values in receipt policy
     * rule expression terms.
     */
    OBFUSCATABLE_CONDITIONS(
            Condition.EQUALS_CASE_SENSITIVE,
            Condition.NOT_EQUALS_CASE_SENSITIVE,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY),

    // Must include all the values from OBFUSCATABLE_CONDITIONS
    RECEIPT_POLICY_CONDITIONS(
            Condition.EQUALS_CASE_SENSITIVE,
            Condition.NOT_EQUALS_CASE_SENSITIVE,
            Condition.IN,
            Condition.IN_DICTIONARY,
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.BETWEEN,
            Condition.CONTAINS,
            Condition.CONTAINS_CASE_SENSITIVE,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.IS_NULL,
            Condition.IS_NOT_NULL,
            Condition.MATCHES_REGEX,
            Condition.MATCHES_REGEX_CASE_SENSITIVE,
            Condition.STARTS_WITH,
            Condition.STARTS_WITH_CASE_SENSITIVE,
            Condition.ENDS_WITH,
            Condition.ENDS_WITH_CASE_SENSITIVE),

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
            Condition.CONTAINS,
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.MATCHES_REGEX,
            Condition.IN),

    // UI Defaults.
    ALL_UI_TEXT(
            Condition.CONTAINS,
            Condition.CONTAINS_CASE_SENSITIVE,
            Condition.EQUALS,
            Condition.EQUALS_CASE_SENSITIVE,
            Condition.NOT_EQUALS,
            Condition.NOT_EQUALS_CASE_SENSITIVE,
            Condition.STARTS_WITH,
            Condition.STARTS_WITH_CASE_SENSITIVE,
            Condition.ENDS_WITH,
            Condition.ENDS_WITH_CASE_SENSITIVE,
            Condition.MATCHES_REGEX,
            Condition.MATCHES_REGEX_CASE_SENSITIVE,
            Condition.IN,
            Condition.IN_DICTIONARY),
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
    ALL_UI_NUMERIC(
            Condition.CONTAINS,
            Condition.CONTAINS_CASE_SENSITIVE,
            Condition.EQUALS,
            Condition.EQUALS_CASE_SENSITIVE,
            Condition.NOT_EQUALS,
            Condition.NOT_EQUALS_CASE_SENSITIVE,
            Condition.STARTS_WITH,
            Condition.STARTS_WITH_CASE_SENSITIVE,
            Condition.ENDS_WITH,
            Condition.ENDS_WITH_CASE_SENSITIVE,
            Condition.MATCHES_REGEX,
            Condition.MATCHES_REGEX_CASE_SENSITIVE,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY),
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
    ALL_UI_DATE(
            Condition.CONTAINS,
            Condition.CONTAINS_CASE_SENSITIVE,
            Condition.EQUALS,
            Condition.EQUALS_CASE_SENSITIVE,
            Condition.NOT_EQUALS,
            Condition.NOT_EQUALS_CASE_SENSITIVE,
            Condition.STARTS_WITH,
            Condition.STARTS_WITH_CASE_SENSITIVE,
            Condition.ENDS_WITH,
            Condition.ENDS_WITH_CASE_SENSITIVE,
            Condition.MATCHES_REGEX,
            Condition.MATCHES_REGEX_CASE_SENSITIVE,
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
            Condition.IN_DICTIONARY),
    DOC_DOC_IS(Condition.IS_DOC_REF),
    DOC_DOC_OF(Condition.OF_DOC_REF),
    DOC_USER_IS(
            Condition.USER_HAS_PERM,
            Condition.USER_HAS_OWNER,
            Condition.USER_HAS_DELETE,
            Condition.USER_HAS_EDIT,
            Condition.USER_HAS_VIEW,
            Condition.USER_HAS_USE),
    RUN_AS_USER(
            Condition.IS_USER_REF);

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
            case DENSE_VECTOR: {
                return ConditionSet.DEFAULT_DENSE_VECTOR;
            }
            case DOC_REF: {
                return ConditionSet.DOC_REF_ALL;
            }
        }
        throw new RuntimeException("Unknown field type");
    }

    public static ConditionSet getSolr(final FieldType fieldType) {
        switch (fieldType) {
            case ID: {
                return ConditionSet.SOLR_ID;
            }
            case BOOLEAN: {
                return ConditionSet.SOLR_BOOLEAN;
            }
            case INTEGER: {
                return ConditionSet.SOLR_NUMERIC;
            }
            case LONG: {
                return ConditionSet.SOLR_NUMERIC;
            }
            case FLOAT: {
                return ConditionSet.SOLR_NUMERIC;
            }
            case DOUBLE: {
                return ConditionSet.SOLR_NUMERIC;
            }
            case DATE: {
                return ConditionSet.SOLR_DATE;
            }
            case TEXT: {
                return ConditionSet.SOLR_TEXT;
            }
            case KEYWORD: {
                return ConditionSet.DEFAULT_KEYWORD;
            }
            case IPV4_ADDRESS: {
                return ConditionSet.SOLR_NUMERIC;
            }
            case DOC_REF: {
                return ConditionSet.DOC_REF_ALL;
            }
        }
        throw new RuntimeException("Unknown field type");
    }

    public static ConditionSet getElastic(final FieldType elasticIndexFieldType) {
        if (FieldType.DATE.equals(elasticIndexFieldType) ||
            FieldType.IPV4_ADDRESS.equals(elasticIndexFieldType) ||
            FieldType.ID.equals(elasticIndexFieldType) ||
            FieldType.LONG.equals(elasticIndexFieldType) ||
            FieldType.INTEGER.equals(elasticIndexFieldType)) {
            return ConditionSet.ELASTIC_NUMERIC;
        } else if (FieldType.DENSE_VECTOR.equals(elasticIndexFieldType)) {
            return ConditionSet.DEFAULT_DENSE_VECTOR;
        } else {
            return ConditionSet.ELASTIC_TEXT;
        }
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

    ConditionSet(final Condition... arr) {
        conditionList = Arrays.asList(arr);
        conditionSet = EnumSet.copyOf(conditionList);
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
