/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.legacy.model_6_1;

import stroom.legacy.model_6_1.ExpressionTerm.Condition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "docRefType", "name", "queryable", "conditions"})
@XmlType(name = "DataSourceField", propOrder = {"type", "docRefType", "name", "queryable", "conditions"})
@Schema(description = "The definition of a field within a data source")
@Deprecated
public final class DataSourceField implements Serializable, HasDisplayValue {

    private static final long serialVersionUID = 1272545271946712570L;

    @XmlElement
    @Schema(description = "The data type for the field",
            required = true)
    private DataSourceFieldType type;

    @XmlElement
    @Schema(description = "The doc ref type for the field",
            required = true)
    private String docRefType;

    @XmlElement
    @Schema(description = "The name of the field",
            example = "field1",
            required = true)
    private String name;

    @XmlElement
    @Schema(description = "Whether the field can be used in predicate in a query",
            example = "true",
            required = true)
    private Boolean queryable;

    /**
     * Defines a list of the {@link Condition} values supported by this field,
     * can be null in which case a default set will be returned. Not persisted
     * in the XML
     */
    @XmlElementWrapper(name = "conditions")
    @XmlElement(name = "condition")
    @Schema(description = "The supported predicate conditions for this field",
            required = true)
    private List<Condition> conditions;

    private DataSourceField() {
    }

    public DataSourceField(final DataSourceFieldType type,
                           final String docRefType,
                           final String name,
                           final Boolean queryable,
                           final List<Condition> conditions) {
        this.type = type;
        this.docRefType = docRefType;
        this.name = name;
        this.queryable = queryable;
        this.conditions = conditions;
    }

    public DataSourceFieldType getType() {
        return type;
    }

    public String getDocRefType() {
        return docRefType;
    }

    public String getName() {
        return name;
    }

    public Boolean getQueryable() {
        return queryable;
    }

    public boolean queryable() {
        return queryable != null && queryable;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    @JsonIgnore
    @XmlTransient
    @Override
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

        final DataSourceField that = (DataSourceField) o;

        if (type != that.type) {
            return false;
        }
        if (!Objects.equals(name, that.name)) {
            return false;
        }
        if (!Objects.equals(queryable, that.queryable)) {
            return false;
        }
        return Objects.equals(conditions, that.conditions);
    }

    @Override
    public int hashCode() {
        int result = type != null
                ? type.hashCode()
                : 0;
        result = 31 * result + (name != null
                ? name.hashCode()
                : 0);
        result = 31 * result + (queryable != null
                ? queryable.hashCode()
                : 0);
        result = 31 * result + (conditions != null
                ? conditions.hashCode()
                : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DataSourceField{" +
               "type=" + type +
               ", name='" + name + '\'' +
               ", queryable=" + queryable +
               ", conditions=" + conditions +
               '}';
    }

    public enum DataSourceFieldType implements HasDisplayValue {
        ID_FIELD("Id", true),
        BOOLEAN_FIELD("Boolean", false),
        INTEGER_FIELD("Integer", true),
        LONG_FIELD("Long", true),
        FLOAT_FIELD("Float", true),
        DOUBLE_FIELD("Double", true),
        DATE_FIELD("Date", false),
        TEXT_FIELD("Text", false),
        DOC_REF("DocRef", false);

        private final String displayValue;
        private final boolean numeric;

        DataSourceFieldType(final String displayValue, final boolean numeric) {
            this.displayValue = displayValue;
            this.numeric = numeric;
        }

        public boolean isNumeric() {
            return numeric;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public static class Builder {

        private DataSourceFieldType type;
        private String docRefType;
        private String name;
        private Boolean queryable;
        private final List<Condition> conditions = new ArrayList<>();

        public Builder type(final DataSourceFieldType value) {
            this.type = value;
            return this;
        }

        public Builder docRefType(final String docRefType) {
            this.docRefType = docRefType;
            return type(DataSourceFieldType.DOC_REF);
        }

        public Builder name(final String value) {
            this.name = value;
            return this;
        }

        public Builder queryable(final Boolean value) {
            this.queryable = value;
            return this;
        }

        public Builder addConditions(final Condition... values) {
            this.conditions.addAll(Arrays.asList(values));
            return this;
        }

        public DataSourceField build() {
            return new DataSourceField(type, docRefType, name, queryable, conditions);
        }
    }
}
