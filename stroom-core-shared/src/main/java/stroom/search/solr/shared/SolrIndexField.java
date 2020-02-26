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

package stroom.search.solr.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@JsonPropertyOrder({
        "fieldUse",
        "fieldName",
        "fieldType",
        "defaultValue",
        "stored",
        "indexed",
        "uninvertible",
        "docValues",
        "multiValued",
        "required",
        "omitNorms",
        "omitTermFreqAndPositions",
        "omitPositions",
        "termVectors",
        "termPositions",
        "termOffsets",
        "termPayloads",
        "sortMissingFirst",
        "sortMissingLast",
        "supportedConditions"
})
@JsonInclude(Include.NON_DEFAULT)
public class SolrIndexField implements HasDisplayValue, Comparable<SolrIndexField>, Serializable {
    public static final String VALID_FIELD_NAME_PATTERN = "[a-zA-Z_](?:[a-zA-Z0-9_])*";
    private static final long serialVersionUID = 3100770758821157580L;

    @JsonProperty
    private SolrIndexFieldType fieldUse;
    @JsonProperty
    private String fieldName;
    @JsonProperty
    private String fieldType;
    @JsonProperty
    private String defaultValue;
    @JsonProperty
    private boolean stored;
    @JsonProperty
    private boolean indexed;
    @JsonProperty
    private boolean uninvertible;
    @JsonProperty
    private boolean docValues;
    @JsonProperty
    private boolean multiValued;
    @JsonProperty
    private boolean required;
    @JsonProperty
    private boolean omitNorms;
    @JsonProperty
    private boolean omitTermFreqAndPositions;
    @JsonProperty
    private boolean omitPositions;
    @JsonProperty
    private boolean termVectors;
    @JsonProperty
    private boolean termPositions;
    @JsonProperty
    private boolean termOffsets;
    @JsonProperty
    private boolean termPayloads;
    @JsonProperty
    private boolean sortMissingFirst;
    @JsonProperty
    private boolean sortMissingLast;

    /**
     * Defines a list of the {@link Condition} values supported by this field,
     * can be null in which case a default set will be returned. Not persisted
     * in the XML
     */
    @JsonProperty
    private List<Condition> supportedConditions;

    public SolrIndexField() {
        fieldUse = SolrIndexFieldType.FIELD;
        indexed = true;
    }

    private SolrIndexField(final SolrIndexFieldType fieldUse,
                           final String fieldName,
                           final boolean stored,
                           final Boolean indexed,
                           final boolean termPositions,
                           final List<Condition> supportedConditions) {
        if (fieldUse != null) {
            this.fieldUse = fieldUse;
        } else {
            this.fieldUse = SolrIndexFieldType.FIELD;
        }
        this.fieldName = fieldName;
        this.stored = stored;
        if (indexed != null) {
            this.indexed = indexed;
        } else {
            this.indexed = true;
        }
        this.termPositions = termPositions;
        if (supportedConditions != null) {
            this.supportedConditions = new ArrayList<>(supportedConditions);
        }
    }

    @JsonCreator
    public SolrIndexField(@JsonProperty("fieldUse") final SolrIndexFieldType fieldUse,
                          @JsonProperty("fieldName") final String fieldName,
                          @JsonProperty("fieldType") final String fieldType,
                          @JsonProperty("defaultValue") final String defaultValue,
                          @JsonProperty("stored") final boolean stored,
                          @JsonProperty("indexed") final Boolean indexed,
                          @JsonProperty("uninvertible") final boolean uninvertible,
                          @JsonProperty("docValues") final boolean docValues,
                          @JsonProperty("multiValued") final boolean multiValued,
                          @JsonProperty("required") final boolean required,
                          @JsonProperty("omitNorms") final boolean omitNorms,
                          @JsonProperty("omitTermFreqAndPositions") final boolean omitTermFreqAndPositions,
                          @JsonProperty("omitPositions") final boolean omitPositions,
                          @JsonProperty("termVectors") final boolean termVectors,
                          @JsonProperty("termPositions") final boolean termPositions,
                          @JsonProperty("termOffsets") final boolean termOffsets,
                          @JsonProperty("termPayloads") final boolean termPayloads,
                          @JsonProperty("sortMissingFirst") final boolean sortMissingFirst,
                          @JsonProperty("sortMissingLast") final boolean sortMissingLast,
                          @JsonProperty("supportedConditions") final List<Condition> supportedConditions) {
        if (fieldUse != null) {
            this.fieldUse = fieldUse;
        } else {
            this.fieldUse = SolrIndexFieldType.FIELD;
        }
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.defaultValue = defaultValue;
        this.stored = stored;
        if (indexed != null) {
            this.indexed = indexed;
        } else {
            this.indexed = true;
        }
        this.uninvertible = uninvertible;
        this.docValues = docValues;
        this.multiValued = multiValued;
        this.required = required;
        this.omitNorms = omitNorms;
        this.omitTermFreqAndPositions = omitTermFreqAndPositions;
        this.omitPositions = omitPositions;
        this.termVectors = termVectors;
        this.termPositions = termPositions;
        this.termOffsets = termOffsets;
        this.termPayloads = termPayloads;
        this.sortMissingFirst = sortMissingFirst;
        this.sortMissingLast = sortMissingLast;
        if (supportedConditions != null) {
            this.supportedConditions = new ArrayList<>(supportedConditions);
        }
    }

    public static SolrIndexField createIdField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.ID, fieldName, true, true, false, null);
    }

    public static SolrIndexField createBooleanField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.BOOLEAN_FIELD, fieldName, false, true, false,
                null);
    }

    public static SolrIndexField createIntegerField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.INTEGER_FIELD, fieldName, false, true, false,
                null);
    }

    public static SolrIndexField createLongField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.LONG_FIELD, fieldName, false, true, false,
                null);
    }

    public static SolrIndexField createFloatField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.FLOAT_FIELD, fieldName, false, true, false,
                null);
    }

    public static SolrIndexField createDoubleField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.DOUBLE_FIELD, fieldName, false, true, false,
                null);
    }


    public static SolrIndexField createDateField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.DATE_FIELD, fieldName, false, true,
                false, null);
    }

    public static SolrIndexField createTextField(final String fieldName) {
        return createTextField(fieldName, false, true, false);
    }

    public static SolrIndexField createTextField(final String fieldName, final boolean stored, final boolean indexed, final boolean termPositions) {
        return new SolrIndexField(SolrIndexFieldType.FIELD, fieldName, stored, indexed,
                termPositions, null);
    }

    public static SolrIndexField create(final SolrIndexFieldType fieldType, final String fieldName,
                                        final boolean stored, final boolean indexed,
                                        final boolean termPositions) {
        return new SolrIndexField(fieldType, fieldName, stored, indexed, termPositions, null);
    }

    public static SolrIndexField create(final SolrIndexFieldType fieldType, final String fieldName,
                                        final boolean stored, final boolean indexed,
                                        final boolean termPositions, final List<Condition> supportedConditions) {
        return new SolrIndexField(fieldType, fieldName, stored, indexed, termPositions,
                supportedConditions);
    }

    public SolrIndexFieldType getFieldUse() {
        return fieldUse;
    }

    public void setFieldUse(final SolrIndexFieldType fieldUse) {
        if (fieldUse == null) {
            this.fieldUse = SolrIndexFieldType.FIELD;
        }
        this.fieldUse = fieldUse;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(final String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(final String fieldType) {
        this.fieldType = fieldType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(final String defaultValue) {
        this.defaultValue = defaultValue;
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

    public boolean isUninvertible() {
        return uninvertible;
    }

    public void setUninvertible(final boolean uninvertible) {
        this.uninvertible = uninvertible;
    }

    public boolean isDocValues() {
        return docValues;
    }

    public void setDocValues(final boolean docValues) {
        this.docValues = docValues;
    }

    public boolean isMultiValued() {
        return multiValued;
    }

    public void setMultiValued(final boolean multiValued) {
        this.multiValued = multiValued;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(final boolean required) {
        this.required = required;
    }

    public boolean isOmitNorms() {
        return omitNorms;
    }

    public void setOmitNorms(final boolean omitNorms) {
        this.omitNorms = omitNorms;
    }

    public boolean isOmitTermFreqAndPositions() {
        return omitTermFreqAndPositions;
    }

    public void setOmitTermFreqAndPositions(final boolean omitTermFreqAndPositions) {
        this.omitTermFreqAndPositions = omitTermFreqAndPositions;
    }

    public boolean isOmitPositions() {
        return omitPositions;
    }

    public void setOmitPositions(final boolean omitPositions) {
        this.omitPositions = omitPositions;
    }

    public boolean isTermVectors() {
        return termVectors;
    }

    public void setTermVectors(final boolean termVectors) {
        this.termVectors = termVectors;
    }

    public boolean isTermPositions() {
        return termPositions;
    }

    public void setTermPositions(final boolean termPositions) {
        this.termPositions = termPositions;
    }

    public boolean isTermOffsets() {
        return termOffsets;
    }

    public void setTermOffsets(final boolean termOffsets) {
        this.termOffsets = termOffsets;
    }

    public boolean isTermPayloads() {
        return termPayloads;
    }

    public void setTermPayloads(final boolean termPayloads) {
        this.termPayloads = termPayloads;
    }

    public boolean isSortMissingFirst() {
        return sortMissingFirst;
    }

    public void setSortMissingFirst(final boolean sortMissingFirst) {
        this.sortMissingFirst = sortMissingFirst;
    }

    public boolean isSortMissingLast() {
        return sortMissingLast;
    }

    public void setSortMissingLast(final boolean sortMissingLast) {
        this.sortMissingLast = sortMissingLast;
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

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return fieldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SolrIndexField)) return false;
        final SolrIndexField that = (SolrIndexField) o;
        return stored == that.stored &&
                indexed == that.indexed &&
                uninvertible == that.uninvertible &&
                docValues == that.docValues &&
                multiValued == that.multiValued &&
                required == that.required &&
                omitNorms == that.omitNorms &&
                omitTermFreqAndPositions == that.omitTermFreqAndPositions &&
                omitPositions == that.omitPositions &&
                termVectors == that.termVectors &&
                termPositions == that.termPositions &&
                termOffsets == that.termOffsets &&
                termPayloads == that.termPayloads &&
                sortMissingFirst == that.sortMissingFirst &&
                sortMissingLast == that.sortMissingLast &&
                fieldUse == that.fieldUse &&
                Objects.equals(fieldName, that.fieldName) &&
                Objects.equals(fieldType, that.fieldType) &&
                Objects.equals(defaultValue, that.defaultValue) &&
                Objects.equals(supportedConditions, that.supportedConditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldUse, fieldName, fieldType, defaultValue, stored, indexed, uninvertible, docValues, multiValued, required, omitNorms, omitTermFreqAndPositions, omitPositions, termVectors, termPositions, termOffsets, termPayloads, sortMissingFirst, sortMissingLast, supportedConditions);
    }

    @Override
    public String toString() {
        return fieldName;
    }

    @Override
    public int compareTo(final SolrIndexField o) {
        return fieldName.compareToIgnoreCase(o.fieldName);
    }

    private List<Condition> getDefaultConditions() {
        final List<Condition> conditions = new ArrayList<>();

        if (fieldUse != null) {
            // First make sure the operator is set.
            switch (fieldUse) {
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
                    if (fieldUse.isNumeric()) {
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
}
