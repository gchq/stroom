/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.index.shared;

import stroom.datasource.api.v2.FieldType;
import stroom.docref.HasDisplayValue;
import stroom.query.api.v2.ExpressionTerm.Condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LuceneIndexField implements HasDisplayValue, Comparable<LuceneIndexField>, Serializable {

    @JsonProperty
    private final FieldType type;
    @Deprecated
    @JsonProperty("fieldType")
    private OldIndexFieldType fieldType;
    @JsonProperty
    private final String fieldName;
    @JsonProperty
    private final AnalyzerType analyzerType;
    @JsonProperty
    private final boolean indexed;
    @JsonProperty
    private final boolean stored;
    @JsonProperty
    private final boolean termPositions;
    @JsonProperty
    private final boolean caseSensitive;

    @JsonCreator
    public LuceneIndexField(@JsonProperty("type") final FieldType type,
                            @Deprecated @JsonProperty("fieldType") final OldIndexFieldType fieldType,
                            @JsonProperty("fieldName") final String fieldName,
                            @JsonProperty("analyzerType") final AnalyzerType analyzerType,
                            @JsonProperty("indexed") final boolean indexed,
                            @JsonProperty("stored") final boolean stored,
                            @JsonProperty("termPositions") final boolean termPositions,
                            @JsonProperty("caseSensitive") final boolean caseSensitive) {
        if (type == null && fieldType != null) {
            this.type = convertLegacyType(fieldType);
        } else {
            this.type = type;
        }
        this.fieldName = fieldName;
        this.analyzerType = analyzerType;
        this.stored = stored;
        this.indexed = indexed;
        this.termPositions = termPositions;
        this.caseSensitive = caseSensitive;
    }

    private FieldType convertLegacyType(final OldIndexFieldType fieldType) {
        switch (fieldType) {
            case ID: {
                return FieldType.ID;
            }
            case BOOLEAN_FIELD: {
                return FieldType.BOOLEAN;
            }
            case INTEGER_FIELD: {
                return FieldType.INTEGER;
            }
            case LONG_FIELD: {
                return FieldType.LONG;
            }
            case FLOAT_FIELD: {
                return FieldType.FLOAT;
            }
            case DOUBLE_FIELD: {
                return FieldType.DOUBLE;
            }
            case DATE_FIELD: {
                return FieldType.DATE;
            }
            case FIELD: {
                return FieldType.TEXT;
            }
            case NUMERIC_FIELD: {
                return FieldType.LONG;
            }
        }
        return FieldType.TEXT;
    }

    public static LuceneIndexField createField(final String fieldName) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName, final AnalyzerType analyzerType) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(analyzerType)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName, final AnalyzerType analyzerType,
                                               final boolean caseSensitive) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .build();
    }

    public static LuceneIndexField createField(final String fieldName,
                                               final AnalyzerType analyzerType,
                                               final boolean caseSensitive,
                                               final boolean stored,
                                               final boolean indexed,
                                               final boolean termPositions) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .stored(stored)
                .indexed(indexed)
                .termPositions(termPositions)
                .build();
    }

    public static LuceneIndexField createNumericField(final String fieldName) {
        return new Builder()
                .type(FieldType.LONG)
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.NUMERIC)
                .build();
    }

    public static LuceneIndexField createIdField(final String fieldName) {
        return new Builder()
                .type(FieldType.ID)
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.KEYWORD)
                .stored(true)
                .build();
    }

    public static LuceneIndexField createDateField(final String fieldName) {
        return new Builder()
                .type(FieldType.DATE)
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public FieldType getType() {
        return type;
    }

    //    @JsonProperty
//    @Deprecated
//    public OldIndexFieldType getFieldType() {
//        if (fieldType == null) {
//            return OldIndexFieldType.FIELD;
//        }
//
//        return fieldType;
//    }

    public String getFieldName() {
        return fieldName;
    }

    public AnalyzerType getAnalyzerType() {
        if (analyzerType == null) {
            return AnalyzerType.KEYWORD;
        }
        return analyzerType;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isStored() {
        return stored;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public boolean isTermPositions() {
        return termPositions;
    }

    @JsonIgnore
    public List<Condition> getSupportedConditions() {
        return getDefaultConditions();
    }

    @Override
    @JsonIgnore
    public String getDisplayValue() {
        return fieldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LuceneIndexField that = (LuceneIndexField) o;
        return stored == that.stored &&
                indexed == that.indexed &&
                termPositions == that.termPositions &&
                caseSensitive == that.caseSensitive &&
                fieldType == that.fieldType &&
                Objects.equals(fieldName, that.fieldName) &&
                analyzerType == that.analyzerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldType, fieldName, stored, indexed, termPositions, analyzerType, caseSensitive);
    }

    @Override
    public String toString() {
        return fieldName;
    }

    @Override
    public int compareTo(final LuceneIndexField o) {
        return fieldName.compareToIgnoreCase(o.fieldName);
    }

    private List<Condition> getDefaultConditions() {
        final List<Condition> conditions = new ArrayList<>();

        if (fieldType != null) {
            // First make sure the operator is set.
            switch (fieldType) {
                case ID:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;

                case FIELD:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;

                case DATE_FIELD:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.GREATER_THAN);
                    conditions.add(Condition.GREATER_THAN_OR_EQUAL_TO);
                    conditions.add(Condition.LESS_THAN);
                    conditions.add(Condition.LESS_THAN_OR_EQUAL_TO);
                    conditions.add(Condition.BETWEEN);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;

                default:
                    if (fieldType.isNumeric()) {
                        conditions.add(Condition.EQUALS);
                        conditions.add(Condition.GREATER_THAN);
                        conditions.add(Condition.GREATER_THAN_OR_EQUAL_TO);
                        conditions.add(Condition.LESS_THAN);
                        conditions.add(Condition.LESS_THAN_OR_EQUAL_TO);
                        conditions.add(Condition.BETWEEN);
                        conditions.add(Condition.IN);
                        conditions.add(Condition.IN_DICTIONARY);
                    }
                    break;
            }
        }

        return conditions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private FieldType type = FieldType.TEXT;
        private String fieldName;
        private AnalyzerType analyzerType = AnalyzerType.KEYWORD;
        private boolean indexed = true;
        private boolean stored;
        private boolean termPositions;
        private boolean caseSensitive;

        private Builder() {
        }

        private Builder(final LuceneIndexField indexField) {
            this.type = indexField.type;
            this.fieldName = indexField.fieldName;
            this.analyzerType = indexField.analyzerType;
            this.indexed = indexField.indexed;
            this.stored = indexField.stored;
            this.termPositions = indexField.termPositions;
            this.caseSensitive = indexField.caseSensitive;
        }

        public Builder type(final FieldType type) {
            this.type = type;
            return this;
        }

        public Builder fieldName(final String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder analyzerType(final AnalyzerType analyzerType) {
            this.analyzerType = analyzerType;
            return this;
        }

        public Builder indexed(final boolean indexed) {
            this.indexed = indexed;
            return this;
        }

        public Builder stored(final boolean stored) {
            this.stored = stored;
            return this;
        }

        public Builder termPositions(final boolean termPositions) {
            this.termPositions = termPositions;
            return this;
        }

        public Builder caseSensitive(final boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public LuceneIndexField build() {
            return new LuceneIndexField(
                    type,
                    null,
                    fieldName,
                    analyzerType,
                    indexed,
                    stored,
                    termPositions,
                    caseSensitive);
        }
    }
}
