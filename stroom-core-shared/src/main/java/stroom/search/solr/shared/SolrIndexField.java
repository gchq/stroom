/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * <p>
 * Wrapper for index field info
 * </p>
 */
@JsonPropertyOrder({
        "fieldUse", // deprecated
        "fieldName", // deprecated
        "fieldType", // deprecated

        "fldName",
        "fldType",
        "nativeType",
        "indexed",
        "stored",
        "defaultValue",
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
        "denseVectorFieldConfig"
})
@JsonInclude(Include.NON_NULL)
public class SolrIndexField implements IndexField {

    public static final String VALID_FIELD_NAME_PATTERN = "[a-zA-Z_](?:[a-zA-Z0-9_])*";

    @Deprecated
    @JsonProperty("fieldUse")
    private OldSolrIndexFieldType fieldUse;
    @Deprecated
    @JsonProperty("fieldName")
    private String fieldName;
    @Deprecated
    @JsonProperty("fieldType")
    private String fieldType;

    @JsonProperty
    private final String fldName;
    @JsonProperty
    private final FieldType fldType;
    @JsonProperty
    private final String nativeType;

    @JsonProperty
    private final String defaultValue;
    @JsonProperty
    private final boolean stored;
    @JsonProperty
    private final boolean indexed;
    @JsonProperty
    private final boolean uninvertible;
    @JsonProperty
    private final boolean docValues;
    @JsonProperty
    private final boolean multiValued;
    @JsonProperty
    private final boolean required;
    @JsonProperty
    private final boolean omitNorms;
    @JsonProperty
    private final boolean omitTermFreqAndPositions;
    @JsonProperty
    private final boolean omitPositions;
    @JsonProperty
    private final boolean termVectors;
    @JsonProperty
    private final boolean termPositions;
    @JsonProperty
    private final boolean termOffsets;
    @JsonProperty
    private final boolean termPayloads;
    @JsonProperty
    private final boolean sortMissingFirst;
    @JsonProperty
    private final boolean sortMissingLast;
    @JsonProperty
    private final DenseVectorFieldConfig denseVectorFieldConfig;

    @JsonCreator
    public SolrIndexField(
            @Deprecated @JsonProperty("fieldUse") final OldSolrIndexFieldType fieldUse,
            @Deprecated @JsonProperty("fieldName") final String fieldName,
            @Deprecated @JsonProperty("fieldType") final String fieldType,

            @JsonProperty("fldName") final String fldName,
            @JsonProperty("fldType") final FieldType fldType,
            @JsonProperty("nativeType") final String nativeType,
            @JsonProperty("defaultValue") final String defaultValue,
            @JsonProperty("indexed") final boolean indexed,
            @JsonProperty("stored") final boolean stored,
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
            @JsonProperty("denseVectorFieldConfig") final DenseVectorFieldConfig denseVectorFieldConfig) {
        this.fldName = convertLegacyName(fldName, fieldName);
        this.fldType = NullSafe.requireNonNullElse(convertLegacyType(fldType, fieldUse), FieldType.TEXT);
        this.nativeType = convertLegacyNativeType(nativeType, fieldType);
        this.stored = stored;
        this.indexed = indexed;
        this.defaultValue = defaultValue;
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
        this.denseVectorFieldConfig = denseVectorFieldConfig;
    }

    private static String convertLegacyName(final String name, final String fieldName) {
        if (name == null) {
            return fieldName;
        }
        return name;
    }

    private static FieldType convertLegacyType(final FieldType type, final OldSolrIndexFieldType fieldType) {
        if (type == null) {
            if (fieldType != null) {
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
            }
            return FieldType.TEXT;
        }
        return type;
    }

    private static String convertLegacyNativeType(final String nativeType, final String fieldType) {
        if (nativeType == null) {
            return fieldType;
        }
        return nativeType;
    }

    public static SolrIndexField createIdField(final String fieldName) {
        return builder()
                .fldType(FieldType.ID)
                .fldName(fieldName)
                .stored(true)
                .build();
    }

    @Override
    public String getFldName() {
        return fldName;
    }

    @Override
    public FieldType getFldType() {
        return fldType;
    }

    public String getNativeType() {
        return nativeType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isStored() {
        return stored;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public boolean isUninvertible() {
        return uninvertible;
    }

    public boolean isDocValues() {
        return docValues;
    }

    public boolean isMultiValued() {
        return multiValued;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isOmitNorms() {
        return omitNorms;
    }

    public boolean isOmitTermFreqAndPositions() {
        return omitTermFreqAndPositions;
    }

    public boolean isOmitPositions() {
        return omitPositions;
    }

    public boolean isTermVectors() {
        return termVectors;
    }

    public boolean isTermPositions() {
        return termPositions;
    }

    public boolean isTermOffsets() {
        return termOffsets;
    }

    public boolean isTermPayloads() {
        return termPayloads;
    }

    public boolean isSortMissingFirst() {
        return sortMissingFirst;
    }

    public boolean isSortMissingLast() {
        return sortMissingLast;
    }

    @JsonIgnore
    @Override
    public AnalyzerType getAnalyzerType() {
        return IndexField.super.getAnalyzerType();
    }

    @JsonIgnore
    @Override
    public boolean isCaseSensitive() {
        return IndexField.super.isCaseSensitive();
    }

    public DenseVectorFieldConfig getDenseVectorFieldConfig() {
        return denseVectorFieldConfig;
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return fldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
               Objects.equals(fldName, that.fldName) &&
               fldType == that.fldType &&
               Objects.equals(nativeType, that.nativeType) &&
               Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                fldName,
                fldType,
                nativeType,
                defaultValue,
                stored,
                indexed,
                uninvertible,
                docValues,
                multiValued,
                required,
                omitNorms,
                omitTermFreqAndPositions,
                omitPositions,
                termVectors,
                termPositions,
                termOffsets,
                termPayloads,
                sortMissingFirst,
                sortMissingLast);
    }

    @Override
    public String toString() {
        return fldName;
    }

    @Override
    public int compareTo(final Field o) {
        return fldName.compareToIgnoreCase(o.getFldName());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private FieldType fldType = FieldType.TEXT;
        private String fldName;
        private String nativeType;
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
        private DenseVectorFieldConfig denseVectorFieldConfig;

        private Builder() {
        }

        private Builder(final SolrIndexField solrIndexField) {
            this.fldType = solrIndexField.fldType;
            this.fldName = solrIndexField.fldName;
            this.nativeType = solrIndexField.nativeType;
            this.defaultValue = solrIndexField.defaultValue;
            this.stored = solrIndexField.stored;
            this.indexed = solrIndexField.indexed;
            this.uninvertible = solrIndexField.uninvertible;
            this.docValues = solrIndexField.docValues;
            this.multiValued = solrIndexField.multiValued;
            this.required = solrIndexField.required;
            this.omitNorms = solrIndexField.omitNorms;
            this.omitTermFreqAndPositions = solrIndexField.omitTermFreqAndPositions;
            this.omitPositions = solrIndexField.omitPositions;
            this.termVectors = solrIndexField.termVectors;
            this.termPositions = solrIndexField.termPositions;
            this.termOffsets = solrIndexField.termOffsets;
            this.termPayloads = solrIndexField.termPayloads;
            this.sortMissingFirst = solrIndexField.sortMissingFirst;
            this.sortMissingLast = solrIndexField.sortMissingLast;
            this.denseVectorFieldConfig = solrIndexField.denseVectorFieldConfig;
        }

        public Builder fldType(final FieldType fldType) {
            this.fldType = fldType;
            return this;
        }

        public Builder fldName(final String fldName) {
            this.fldName = fldName;
            return this;
        }

        public Builder nativeType(final String nativeType) {
            this.nativeType = nativeType;
            return this;
        }

        public Builder defaultValue(final String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder stored(final boolean stored) {
            this.stored = stored;
            return this;
        }

        public Builder indexed(final boolean indexed) {
            this.indexed = indexed;
            return this;
        }

        public Builder uninvertible(final boolean uninvertible) {
            this.uninvertible = uninvertible;
            return this;
        }

        public Builder docValues(final boolean docValues) {
            this.docValues = docValues;
            return this;
        }

        public Builder multiValued(final boolean multiValued) {
            this.multiValued = multiValued;
            return this;
        }

        public Builder required(final boolean required) {
            this.required = required;
            return this;
        }

        public Builder omitNorms(final boolean omitNorms) {
            this.omitNorms = omitNorms;
            return this;
        }

        public Builder omitTermFreqAndPositions(final boolean omitTermFreqAndPositions) {
            this.omitTermFreqAndPositions = omitTermFreqAndPositions;
            return this;
        }

        public Builder omitPositions(final boolean omitPositions) {
            this.omitPositions = omitPositions;
            return this;
        }

        public Builder termVectors(final boolean termVectors) {
            this.termVectors = termVectors;
            return this;
        }

        public Builder termPositions(final boolean termPositions) {
            this.termPositions = termPositions;
            return this;
        }

        public Builder termOffsets(final boolean termOffsets) {
            this.termOffsets = termOffsets;
            return this;
        }

        public Builder termPayloads(final boolean termPayloads) {
            this.termPayloads = termPayloads;
            return this;
        }


        public Builder sortMissingFirst(final boolean sortMissingFirst) {
            this.sortMissingFirst = sortMissingFirst;
            return this;
        }

        public Builder sortMissingLast(final boolean sortMissingLast) {
            this.sortMissingLast = sortMissingLast;
            return this;
        }

        public Builder denseVectorFieldConfig(final DenseVectorFieldConfig denseVectorFieldConfig) {
            this.denseVectorFieldConfig = denseVectorFieldConfig;
            return this;
        }

        public SolrIndexField build() {
            return new SolrIndexField(
                    null,
                    null,
                    null,
                    fldName,
                    fldType,
                    nativeType,
                    defaultValue,
                    indexed,
                    stored,
                    uninvertible,
                    docValues,
                    multiValued,
                    required,
                    omitNorms,
                    omitTermFreqAndPositions,
                    omitPositions,
                    termVectors,
                    termPositions,
                    termOffsets,
                    termPayloads,
                    sortMissingFirst,
                    sortMissingLast,
                    denseVectorFieldConfig);
        }
    }
}
