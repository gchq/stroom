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

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
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
        "indexed"
})
@JsonInclude(Include.NON_NULL)
public class ElasticIndexField implements HasDisplayValue, Comparable<ElasticIndexField>, Serializable {
    @JsonProperty
    private ElasticIndexFieldType fieldUse;

    @JsonProperty
    private String fieldName;

    @JsonProperty
    private String fieldType;

    @JsonProperty
    private boolean indexed;

    public ElasticIndexField() { }

    @JsonCreator
    public ElasticIndexField(
            @JsonProperty("fieldUse") final ElasticIndexFieldType fieldUse,
            @JsonProperty("fieldName") final String fieldName,
            @JsonProperty("fieldType") final String fieldType,
            @JsonProperty("indexed") final boolean indexed
    ) {
        setFieldUse(fieldUse);
        setFieldName(fieldName);
        setFieldType(fieldType);
        setIndexed(indexed);
    }

    public ElasticIndexFieldType getFieldUse() {
        return fieldUse;
    }

    public void setFieldUse(final ElasticIndexFieldType fieldUse) {
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

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(final boolean indexed) {
        this.indexed = indexed;
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return fieldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ElasticIndexField)) {
            return false;
        }
        final ElasticIndexField that = (ElasticIndexField) o;
        return indexed == that.indexed &&
                fieldUse == that.fieldUse &&
                Objects.equals(fieldName, that.fieldName) &&
                Objects.equals(fieldType, that.fieldType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldUse, fieldName, fieldType, indexed);
    }

    @Override
    public String toString() {
        return fieldName;
    }

    @Override
    public int compareTo(final ElasticIndexField o) {
        return fieldName.compareToIgnoreCase(o.fieldName);
    }
}
