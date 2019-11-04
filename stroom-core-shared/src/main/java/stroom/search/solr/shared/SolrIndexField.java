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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.query.api.v2.ExpressionTerm.Condition;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@JsonInclude(Include.NON_DEFAULT)
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
        "sortMissingLast"
})
public class SolrIndexField implements HasDisplayValue, Comparable<SolrIndexField>, Serializable {
    private static final long serialVersionUID = 3100770758821157580L;

    public static final String VALID_FIELD_NAME_PATTERN = "[a-zA-Z_](?:[a-zA-Z0-9_])*";

    private SolrIndexFieldType fieldUse = SolrIndexFieldType.TEXT_FIELD;
    private String fieldName;
    private String fieldType;
    private String defaultValue;
    private boolean stored;
    private boolean indexed = true;
    private boolean uninvertible;
    private boolean docValues;
    private boolean multiValued;
    private boolean required;
    private boolean omitNorms;
    private boolean omitTermFreqAndPositions;
    private boolean omitPositions;
    private boolean termVectors;
    private boolean termPositions;
    private boolean termOffsets;
    private boolean termPayloads;
    private boolean sortMissingFirst;
    private boolean sortMissingLast;

    /**
     * Defines a list of the {@link Condition} values supported by this field,
     * can be null in which case a default set will be returned. Not persisted
     * in the XML
     */
    @JsonIgnore
    @XmlTransient
    private List<Condition> supportedConditions;

    public SolrIndexField() {
        // Default constructor necessary for GWT serialisation.
    }

    private SolrIndexField(final SolrIndexFieldType fieldUse,
                           final String fieldName,
                           final boolean stored,
                           final boolean indexed,
                           final boolean termPositions,
                           final List<Condition> supportedConditions) {
        setFieldUse(fieldUse);
        setFieldName(fieldName);
        setStored(stored);
        setIndexed(indexed);
        setTermPositions(termPositions);

        if (supportedConditions != null) {
            this.supportedConditions = new ArrayList<>(supportedConditions);
        }
    }

    public static SolrIndexField createIdField(final String fieldName) {
        return new SolrIndexField(SolrIndexFieldType.ID_FIELD, fieldName, true, true, false, null);
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
        return new SolrIndexField(SolrIndexFieldType.TEXT_FIELD, fieldName, stored, indexed,
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
            this.fieldUse = SolrIndexFieldType.TEXT_FIELD;
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
                case ID_FIELD:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;
                case TEXT_FIELD:
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
