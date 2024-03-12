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

package stroom.search.elastic.shared;

import stroom.datasource.api.v2.Field;
import stroom.datasource.api.v2.FieldType;
import stroom.index.shared.IndexField;

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

        "name",
        "type",
        "nativeType",
        "indexed"
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
    private final String name;
    @JsonProperty
    private final FieldType type;
    @JsonProperty
    private final String nativeType;
    @JsonProperty
    private final boolean indexed;

    @JsonCreator
    public ElasticIndexField(
            @Deprecated @JsonProperty("fieldName") final String fieldName,
            @Deprecated @JsonProperty("fieldUse") final FieldType fieldUse,
            @Deprecated @JsonProperty("fieldType") final String fieldType,

            @JsonProperty("name") final String name,
            @JsonProperty("type") final FieldType type,
            @JsonProperty("nativeType") final String nativeType,
            @JsonProperty("indexed") final boolean indexed) {
        this.name = convertLegacyName(name, fieldName);
        this.type = convertLegacyType(type, fieldUse);
        this.nativeType = convertLegacyNativeType(nativeType, fieldType);
        this.indexed = indexed;
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

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public String getNativeType() {
        return nativeType;
    }

    public boolean isIndexed() {
        return indexed;
    }

    @Override
    @JsonIgnore
    public String getDisplayValue() {
        return name;
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
                Objects.equals(name, that.name) &&
                type == that.type &&
                Objects.equals(nativeType, that.nativeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldUse, fieldName, fieldType, name, type, nativeType, indexed);
    }

    @Override
    public int compareTo(final Field o) {
        return name.compareToIgnoreCase(o.getName());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String name;
        private FieldType type = FieldType.TEXT;
        private String nativeType;
        private boolean indexed = true;

        private Builder() {
        }

        private Builder(final ElasticIndexField indexField) {
            this.name = indexField.name;
            this.type = indexField.type;
            this.nativeType = indexField.nativeType;
            this.indexed = indexField.indexed;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder type(final FieldType type) {
            this.type = type;
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


        public ElasticIndexField build() {
            return new ElasticIndexField(
                    null,
                    null,
                    null,
                    name,
                    type,
                    nativeType,
                    indexed);
        }
    }
}
