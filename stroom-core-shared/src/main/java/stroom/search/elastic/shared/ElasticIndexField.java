/*
 * Copyright 2021 Crown Copyright
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

package stroom.search.elastic.shared;

import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryOptionSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;
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
        "denseVectorFieldConfig",
        "virtual",
        "multiFieldParent",
        "sourceIndices",
        "conflictingTypes",
        "mappingParameters",
        "queryOptionSet",
        "supportsTopValues"
})
@JsonInclude(Include.NON_NULL)
public class ElasticIndexField implements IndexField {

    @Deprecated
    @JsonProperty("fieldUse")
    private FieldType fieldUse;
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
    private final boolean indexed;
    @JsonProperty
    private final DenseVectorFieldConfig denseVectorFieldConfig;
    /// Whether this is a virtual field, defined in the index doc's field settings rather than the index mapping
    @JsonProperty
    @JsonInclude(Include.NON_DEFAULT)
    private final boolean virtual;
    /// For a multi-field (e.g. `title.keyword`), the full name of the field it belongs to (e.g. `title`)
    @JsonProperty
    private final String multiFieldParent;
    /// The concrete indices whose mappings hold this field
    @JsonProperty
    private final List<String> sourceIndices;
    /// When source indices map this field to different types, the native type in each index, keyed by index name
    @JsonProperty
    private final Map<String, String> conflictingTypes;
    /// The mapping parameters set for this field, other than its type and child fields, keyed by parameter name
    @JsonProperty
    private final Map<String, String> mappingParameters;
    /// The query options the field's terms may set, or `null` if it has none
    @JsonProperty
    private final QueryOptionSet queryOptionSet;
    /// Whether the value picker can list the field's most common values; `null` means it can't
    @JsonProperty
    private final Boolean supportsTopValues;

    @JsonCreator
    public ElasticIndexField(
            @Deprecated @JsonProperty("fieldName") final String fieldName,
            @Deprecated @JsonProperty("fieldUse") final FieldType fieldUse,
            @Deprecated @JsonProperty("fieldType") final String fieldType,

            @JsonProperty("fldName") final String fldName,
            @JsonProperty("fldType") final FieldType fldType,
            @JsonProperty("nativeType") final String nativeType,
            @JsonProperty("indexed") final Boolean indexed,
            @JsonProperty("denseVectorFieldConfig") final DenseVectorFieldConfig denseVectorFieldConfig,
            @JsonProperty("virtual") final Boolean virtual,
            @JsonProperty("multiFieldParent") final String multiFieldParent,
            @JsonProperty("sourceIndices") final List<String> sourceIndices,
            @JsonProperty("conflictingTypes") final Map<String, String> conflictingTypes,
            @JsonProperty("mappingParameters") final Map<String, String> mappingParameters,
            @JsonProperty("queryOptionSet") final QueryOptionSet queryOptionSet,
            @JsonProperty("supportsTopValues") final Boolean supportsTopValues) {
        this.fldName = convertLegacyName(fldName, fieldName);
        this.fldType = convertLegacyType(fldType, fieldUse);
        this.nativeType = convertLegacyNativeType(nativeType, fieldType);
        this.indexed = Objects.requireNonNullElse(indexed, false);
        this.denseVectorFieldConfig = denseVectorFieldConfig;
        this.virtual = Boolean.TRUE.equals(virtual);
        this.multiFieldParent = multiFieldParent;
        this.sourceIndices = emptyToNull(sourceIndices);
        this.conflictingTypes = emptyToNull(conflictingTypes);
        this.mappingParameters = emptyToNull(mappingParameters);
        this.queryOptionSet = queryOptionSet;
        this.supportsTopValues = Boolean.TRUE.equals(supportsTopValues)
                ? Boolean.TRUE
                : null;
    }

    private static <T> List<T> emptyToNull(final List<T> list) {
        return list == null || list.isEmpty()
                ? null
                : list;
    }

    private static <K, V> Map<K, V> emptyToNull(final Map<K, V> map) {
        return map == null || map.isEmpty()
                ? null
                : map;
    }

    private static String convertLegacyName(final String name, final String fieldName) {
        if (name == null) {
            return fieldName;
        }
        return name;
    }

    private static FieldType convertLegacyType(final FieldType type, final FieldType fieldUse) {
        if (type == null) {
            if (fieldUse != null) {
                return fieldUse;
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

    public boolean isIndexed() {
        return indexed;
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

    @JsonIgnore
    @Override
    public boolean isStored() {
        return IndexField.super.isStored();
    }

    @JsonIgnore
    @Override
    public boolean isTermPositions() {
        return IndexField.super.isTermPositions();
    }

    public DenseVectorFieldConfig getDenseVectorFieldConfig() {
        return denseVectorFieldConfig;
    }

    /// @return `true` if this is a virtual field defined in the index doc's field settings
    public boolean isVirtual() {
        return virtual;
    }

    /// @return for a multi-field, the full name of the field it belongs to, otherwise `null`
    public String getMultiFieldParent() {
        return multiFieldParent;
    }

    /// @return the concrete indices whose mappings hold this field, or `null` if not known
    public List<String> getSourceIndices() {
        return sourceIndices;
    }

    /// @return the native type of this field in each source index, keyed by index name, when the indices
    /// disagree on its type; otherwise `null`
    public Map<String, String> getConflictingTypes() {
        return conflictingTypes;
    }

    /// @return `true` if source indices map this field to different types
    @JsonIgnore
    public boolean hasTypeConflict() {
        return conflictingTypes != null;
    }

    /// @return the mapping parameters set for this field, keyed by parameter name, or `null` if none
    public Map<String, String> getMappingParameters() {
        return mappingParameters;
    }

    /// @return the query options the field's terms may set, or `null` if it has none
    public QueryOptionSet getQueryOptionSet() {
        return queryOptionSet;
    }

    /// @return `Boolean.TRUE` if the value picker can list the field's most common values, otherwise `null`
    public Boolean getSupportsTopValues() {
        return supportsTopValues;
    }

    @Override
    @JsonIgnore
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
        final ElasticIndexField that = (ElasticIndexField) o;
        return indexed == that.indexed &&
               virtual == that.virtual &&
               fieldUse == that.fieldUse &&
               Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(fieldType, that.fieldType) &&
               Objects.equals(fldName, that.fldName) &&
               fldType == that.fldType &&
               Objects.equals(nativeType, that.nativeType) &&
               Objects.equals(multiFieldParent, that.multiFieldParent) &&
               Objects.equals(sourceIndices, that.sourceIndices) &&
               Objects.equals(conflictingTypes, that.conflictingTypes) &&
               Objects.equals(mappingParameters, that.mappingParameters) &&
               Objects.equals(queryOptionSet, that.queryOptionSet) &&
               Objects.equals(supportsTopValues, that.supportsTopValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldUse, fieldName, fieldType, fldName, fldType, nativeType, indexed, virtual,
                multiFieldParent, sourceIndices, conflictingTypes, mappingParameters, queryOptionSet,
                supportsTopValues);
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

        private String fldName;
        private FieldType fldType = FieldType.TEXT;
        private String nativeType;
        private boolean indexed = true;
        private DenseVectorFieldConfig denseVectorFieldConfig;
        private boolean virtual;
        private String multiFieldParent;
        private List<String> sourceIndices;
        private Map<String, String> conflictingTypes;
        private Map<String, String> mappingParameters;
        private QueryOptionSet queryOptionSet;
        private Boolean supportsTopValues;

        private Builder() {
        }

        private Builder(final ElasticIndexField indexField) {
            this.fldName = indexField.fldName;
            this.fldType = indexField.fldType;
            this.nativeType = indexField.nativeType;
            this.indexed = indexField.indexed;
            this.denseVectorFieldConfig = indexField.denseVectorFieldConfig;
            this.virtual = indexField.virtual;
            this.multiFieldParent = indexField.multiFieldParent;
            this.sourceIndices = indexField.sourceIndices;
            this.conflictingTypes = indexField.conflictingTypes;
            this.mappingParameters = indexField.mappingParameters;
            this.queryOptionSet = indexField.queryOptionSet;
            this.supportsTopValues = indexField.supportsTopValues;
        }

        public Builder fldName(final String fldName) {
            this.fldName = fldName;
            return this;
        }

        public Builder fldType(final FieldType fldType) {
            this.fldType = fldType;
            return this;
        }

        public Builder nativeType(final String nativeType) {
            this.nativeType = nativeType;
            return this;
        }

        public Builder indexed(final boolean indexed) {
            this.indexed = indexed;
            return this;
        }

        public Builder denseVectorFieldConfig(final DenseVectorFieldConfig denseVectorFieldConfig) {
            this.denseVectorFieldConfig = denseVectorFieldConfig;
            return this;
        }

        /// @param virtual `true` if this is a virtual field
        /// @return this builder
        public Builder virtual(final boolean virtual) {
            this.virtual = virtual;
            return this;
        }

        /// @param multiFieldParent for a multi-field, the full name of the field it belongs to
        /// @return this builder
        public Builder multiFieldParent(final String multiFieldParent) {
            this.multiFieldParent = multiFieldParent;
            return this;
        }

        /// @param sourceIndices the concrete indices whose mappings hold the field
        /// @return this builder
        public Builder sourceIndices(final List<String> sourceIndices) {
            this.sourceIndices = sourceIndices;
            return this;
        }

        /// @param conflictingTypes the native type in each source index, keyed by index name, when they differ
        /// @return this builder
        public Builder conflictingTypes(final Map<String, String> conflictingTypes) {
            this.conflictingTypes = conflictingTypes;
            return this;
        }

        /// @param mappingParameters the mapping parameters set for the field, keyed by parameter name
        /// @return this builder
        public Builder mappingParameters(final Map<String, String> mappingParameters) {
            this.mappingParameters = mappingParameters;
            return this;
        }

        /// @param queryOptionSet the query options the field's terms may set, or `null`
        /// @return this builder
        public Builder queryOptionSet(final QueryOptionSet queryOptionSet) {
            this.queryOptionSet = queryOptionSet;
            return this;
        }

        /// @param supportsTopValues `true` if the value picker can list the field's most common values
        /// @return this builder
        public Builder supportsTopValues(final Boolean supportsTopValues) {
            this.supportsTopValues = supportsTopValues;
            return this;
        }

        public ElasticIndexField build() {
            return new ElasticIndexField(
                    null,
                    null,
                    null,
                    fldName,
                    fldType,
                    nativeType,
                    indexed,
                    denseVectorFieldConfig,
                    virtual,
                    multiFieldParent,
                    sourceIndices,
                    conflictingTypes,
                    mappingParameters,
                    queryOptionSet,
                    supportsTopValues);
        }
    }
}
