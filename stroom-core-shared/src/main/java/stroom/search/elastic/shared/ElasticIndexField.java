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

package stroom.search.elastic.shared;

import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.Field;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;

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
        "denseVectorFieldConfig"
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

    @JsonCreator
    public ElasticIndexField(
            @Deprecated @JsonProperty("fieldName") final String fieldName,
            @Deprecated @JsonProperty("fieldUse") final FieldType fieldUse,
            @Deprecated @JsonProperty("fieldType") final String fieldType,

            @JsonProperty("fldName") final String fldName,
            @JsonProperty("fldType") final FieldType fldType,
            @JsonProperty("nativeType") final String nativeType,
            @JsonProperty("indexed") final boolean indexed,
            @JsonProperty("denseVectorFieldConfig") final DenseVectorFieldConfig denseVectorFieldConfig) {
        this.fldName = convertLegacyName(fldName, fieldName);
        this.fldType = convertLegacyType(fldType, fieldUse);
        this.nativeType = convertLegacyNativeType(nativeType, fieldType);
        this.indexed = indexed;
        this.denseVectorFieldConfig = denseVectorFieldConfig;
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
               fieldUse == that.fieldUse &&
               Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(fieldType, that.fieldType) &&
               Objects.equals(fldName, that.fldName) &&
               fldType == that.fldType &&
               Objects.equals(nativeType, that.nativeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldUse, fieldName, fieldType, fldName, fldType, nativeType, indexed);
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

        private Builder() {
        }

        private Builder(final ElasticIndexField indexField) {
            this.fldName = indexField.fldName;
            this.fldType = indexField.fldType;
            this.nativeType = indexField.nativeType;
            this.indexed = indexField.indexed;
            this.denseVectorFieldConfig = indexField.denseVectorFieldConfig;
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

        public ElasticIndexField build() {
            return new ElasticIndexField(
                    null,
                    null,
                    null,
                    fldName,
                    fldType,
                    nativeType,
                    indexed,
                    denseVectorFieldConfig);
        }
    }
}
