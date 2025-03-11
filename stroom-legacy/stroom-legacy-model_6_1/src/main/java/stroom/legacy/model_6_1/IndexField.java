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

package stroom.legacy.model_6_1;

import stroom.legacy.model_6_1.ExpressionTerm.Condition;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@XmlType(name = "indexField", propOrder = {
        "analyzerType", "caseSensitive", "fieldName", "fieldType", "indexed",
        "stored", "termPositions"})
@Deprecated
public class IndexField implements HasDisplayValue, Comparable<IndexField>, Serializable {

    private static final long serialVersionUID = 3100770758821157580L;

    @XmlElement(name = "fieldType")
    private IndexFieldType fieldType;
    @XmlElement(name = "fieldName")
    private String fieldName;
    @XmlElement(name = "stored")
    private boolean stored = false;

    /**
     * Determines whether the field can be queried or not
     */
    @XmlElement(name = "indexed")
    private boolean indexed = true;
    @XmlElement(name = "termPositions")
    private boolean termPositions = false;
    @XmlElement(name = "analyzerType")
    private AnalyzerType analyzerType;
    @XmlElement(name = "caseSensitive")
    private boolean caseSensitive = false;

    /**
     * Defines a list of the {@link Condition} values supported by this field,
     * can be null in which case a default set will be returned. Not persisted
     * in the XML
     */
    @XmlTransient
    private List<Condition> supportedConditions;

    public IndexField() {
        // Default constructor necessary for GWT serialisation.
    }

    private IndexField(final IndexFieldType fieldType,
                       final String fieldName,
                       final AnalyzerType analyzerType,
                       final boolean caseSensitive,
                       final boolean stored,
                       final boolean indexed,
                       final boolean termPositions,
                       final List<ExpressionTerm.Condition> supportedConditions) {
        setFieldType(fieldType);
        setFieldName(fieldName);
        setAnalyzerType(analyzerType);
        setCaseSensitive(caseSensitive);
        setStored(stored);
        setIndexed(indexed);
        setTermPositions(termPositions);

        if (supportedConditions != null) {
            this.supportedConditions = new ArrayList<>(supportedConditions);
        }
    }

    public static IndexField createField(final String fieldName) {
        return createField(fieldName, AnalyzerType.ALPHA_NUMERIC, false, false, true, false);
    }

    public static IndexField createField(final String fieldName, final AnalyzerType analyzerType) {
        return createField(fieldName, analyzerType, false, false, true, false);
    }

    public static IndexField createField(final String fieldName, final AnalyzerType analyzerType,
                                         final boolean caseSensitive) {
        return createField(fieldName, analyzerType, caseSensitive, false, true, false);
    }

    public static IndexField createField(final String fieldName,
                                         final AnalyzerType analyzerType,
                                         final boolean caseSensitive,
                                         final boolean stored,
                                         final boolean indexed,
                                         final boolean termPositions) {
        return new IndexField(IndexFieldType.FIELD, fieldName, analyzerType, caseSensitive, stored, indexed,
                termPositions, null);
    }

    public static IndexField createNumericField(final String fieldName) {
        return new IndexField(IndexFieldType.NUMERIC_FIELD, fieldName, AnalyzerType.NUMERIC, false, false, true, false,
                null);
    }

    public static IndexField createIdField(final String fieldName) {
        return new IndexField(IndexFieldType.ID, fieldName, AnalyzerType.KEYWORD, false, true, true, false, null);
    }

    public static IndexField createDateField(final String fieldName) {
        return new IndexField(IndexFieldType.DATE_FIELD, fieldName, AnalyzerType.ALPHA_NUMERIC, false, false, true,
                false, null);
    }

    public static IndexField create(final IndexFieldType fieldType,
                                    final String fieldName,
                                    final AnalyzerType analyzerType,
                                    final boolean caseSensitive,
                                    final boolean stored,
                                    final boolean indexed,
                                    final boolean termPositions) {
        return new IndexField(fieldType, fieldName, analyzerType, caseSensitive, stored, indexed, termPositions, null);
    }

    public static IndexField create(final IndexFieldType fieldType,
                                    final String fieldName,
                                    final AnalyzerType analyzerType,
                                    final boolean caseSensitive,
                                    final boolean stored,
                                    final boolean indexed,
                                    final boolean termPositions,
                                    final List<Condition> supportedConditions) {
        return new IndexField(fieldType, fieldName, analyzerType, caseSensitive, stored, indexed, termPositions,
                supportedConditions);
    }

    public IndexFieldType getFieldType() {
        if (fieldType == null) {
            return IndexFieldType.FIELD;
        }

        return fieldType;
    }

    public void setFieldType(final IndexFieldType fieldType) {
        this.fieldType = fieldType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(final String fieldName) {
        this.fieldName = fieldName;
    }

    public AnalyzerType getAnalyzerType() {
        if (analyzerType == null) {
            return AnalyzerType.KEYWORD;
        }
        return analyzerType;
    }

    public void setAnalyzerType(final AnalyzerType analyzerType) {
        this.analyzerType = analyzerType;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(final boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public boolean isStored() {
        return stored;
    }

    public void setStored(final boolean stored) {
        this.stored = stored;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(final boolean indexed) {
        this.indexed = indexed;
    }

    public boolean isTermPositions() {
        return termPositions;
    }

    public void setTermPositions(final boolean termPositions) {
        this.termPositions = termPositions;
    }

    public List<Condition> getSupportedConditions() {
        if (supportedConditions == null) {
            return getDefaultConditions();
        } else {
            return supportedConditions;
        }
    }

    public void setSupportedConditions(final List<Condition> supportedConditions) {
        if (supportedConditions == null) {
            this.supportedConditions = null;
        } else {
            this.supportedConditions = new ArrayList<>(supportedConditions);
        }
    }

    @Override
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

    public enum AnalyzerType implements HasDisplayValue {
        KEYWORD("Keyword"),
        ALPHA("Alpha"),
        NUMERIC("Numeric"),
        ALPHA_NUMERIC("Alpha numeric"),
        WHITESPACE(
                "Whitespace"),
        STOP("Stop words"),
        STANDARD("Standard");

        private final String displayValue;

        AnalyzerType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
