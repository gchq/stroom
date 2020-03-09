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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.HasDisplayValue;
import stroom.query.api.v2.ExpressionTerm.Condition;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "indexField", propOrder = {"analyzerType", "caseSensitive", "fieldName", "fieldType", "indexed", "stored", "termPositions"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexField implements HasDisplayValue, Comparable<IndexField>, Serializable {
    private static final long serialVersionUID = 3100770758821157580L;

    @XmlElement(name = "fieldType")
    @JsonProperty
    private final IndexFieldType fieldType;
    @XmlElement(name = "fieldName")
    @JsonProperty
    private final String fieldName;
    @XmlElement(name = "analyzerType")
    @JsonProperty
    private final AnalyzerType analyzerType;
    @XmlElement(name = "indexed")
    @JsonProperty
    private final boolean indexed;
    @XmlElement(name = "stored")
    @JsonProperty
    private final boolean stored;
    @XmlElement(name = "termPositions")
    @JsonProperty
    private final boolean termPositions;
    @XmlElement(name = "caseSensitive")
    @JsonProperty
    private final boolean caseSensitive;

    /**
     * Defines a list of the {@link Condition} values supported by this field,
     * can be null in which case a default set will be returned. Not persisted
     * in the XML
     */
    @XmlTransient
    @JsonIgnore
    private List<Condition> supportedConditions;

    @JsonCreator
    public IndexField(@JsonProperty("fieldType") final IndexFieldType fieldType,
                      @JsonProperty("fieldName") final String fieldName,
                      @JsonProperty("analyzerType") final AnalyzerType analyzerType,
                      @JsonProperty("indexed") final boolean indexed,
                      @JsonProperty("stored") final boolean stored,
                      @JsonProperty("termPositions") final boolean termPositions,
                      @JsonProperty("caseSensitive") final boolean caseSensitive,
                      @JsonProperty("supportedConditions") final List<Condition> supportedConditions) {
        this.fieldType = fieldType;
        this.fieldName = fieldName;
        this.analyzerType = analyzerType;
        this.stored = stored;
        this.indexed = indexed;
        this.termPositions = termPositions;
        this.caseSensitive = caseSensitive;
        this.supportedConditions = supportedConditions;
    }

    public static IndexField createField(final String fieldName) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public static IndexField createField(final String fieldName, final AnalyzerType analyzerType) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(analyzerType)
                .build();
    }

    public static IndexField createField(final String fieldName, final AnalyzerType analyzerType,
                                         final boolean caseSensitive) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .build();
    }

    public static IndexField createField(final String fieldName, final AnalyzerType analyzerType,
                                         final boolean caseSensitive, final boolean stored, final boolean indexed, final boolean termPositions) {
        return new Builder()
                .fieldName(fieldName)
                .analyzerType(analyzerType)
                .caseSensitive(caseSensitive)
                .stored(stored)
                .indexed(indexed)
                .termPositions(termPositions)
                .build();
    }

    public static IndexField createNumericField(final String fieldName) {
        return new Builder()
                .fieldType(IndexFieldType.NUMERIC_FIELD)
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.NUMERIC)
                .build();
    }

    public static IndexField createIdField(final String fieldName) {
        return new Builder()
                .fieldType(IndexFieldType.ID)
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.KEYWORD)
                .stored(true)
                .build();
    }

    public static IndexField createDateField(final String fieldName) {
        return new Builder()
                .fieldType(IndexFieldType.DATE_FIELD)
                .fieldName(fieldName)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC)
                .build();
    }

    public IndexFieldType getFieldType() {
        if (fieldType == null) {
            return IndexFieldType.FIELD;
        }

        return fieldType;
    }

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
        if (supportedConditions == null) {
            return getDefaultConditions();
        } else {
            return supportedConditions;
        }
    }

    @JsonIgnore
    public void setSupportedConditions(final List<Condition> supportedConditions) {
        if (supportedConditions == null) {
            this.supportedConditions = null;
        } else {
            this.supportedConditions = new ArrayList<>(supportedConditions);
        }
    }

    @Override
    @JsonIgnore
    public String getDisplayValue() {
        return fieldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final IndexField that = (IndexField) o;
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
    public int compareTo(final IndexField o) {
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

    public static class Builder {
        private IndexFieldType fieldType = IndexFieldType.FIELD;
        private String fieldName;
        private AnalyzerType analyzerType = AnalyzerType.KEYWORD;
        private boolean indexed = true;
        private boolean stored;
        private boolean termPositions;
        private boolean caseSensitive;
        private List<Condition> supportedConditions;

        public Builder fieldType(final IndexFieldType fieldType) {
            this.fieldType = fieldType;
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

        public Builder supportedConditions(final List<Condition> supportedConditions) {
            this.supportedConditions = new ArrayList<>(supportedConditions);
            return this;
        }

        public IndexField build() {
            return new IndexField(fieldType, fieldName, analyzerType, indexed, stored, termPositions, caseSensitive, supportedConditions);
        }
    }
}
