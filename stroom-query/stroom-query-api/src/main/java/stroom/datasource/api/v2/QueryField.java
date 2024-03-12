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

package stroom.datasource.api.v2;

import stroom.docref.HasDisplayValue;
import stroom.query.api.v2.ExpressionTerm.Condition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonPropertyOrder({"type", "docRefType", "name", "queryable", "conditionSet"})
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @Type(value = IdField.class, name = "Id"),
        @Type(value = BooleanField.class, name = "Boolean"),
        @Type(value = IntegerField.class, name = "Integer"),
        @Type(value = LongField.class, name = "Long"),
        @Type(value = FloatField.class, name = "Float"),
        @Type(value = DoubleField.class, name = "Double"),
        @Type(value = DateField.class, name = "Date"),
        @Type(value = TextField.class, name = "Text"),
        @Type(value = KeywordField.class, name = "Keyword"),
        @Type(value = IpV4AddressField.class, name = "IpV4Address"),
        @Type(value = DocRefField.class, name = "DocRef")
})
@JsonInclude(Include.NON_NULL)
public abstract class QueryField implements Field, HasDisplayValue {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final ConditionSet conditionSet;
    @JsonProperty
    private final String docRefType;
    @JsonProperty
    private final Boolean queryable;

    public QueryField(final String name,
                      final ConditionSet conditionSet,
                      final String docRefType,
                      final Boolean queryable) {
        this.name = name;
        this.conditionSet = conditionSet;
        this.docRefType = docRefType;
        this.queryable = queryable;
    }

    public QueryField(final String name,
                      final Boolean queryable,
                      final ConditionSet conditionSet) {
        this(name, conditionSet, null, queryable);
    }

    public abstract FieldType getFieldType();

    @Override
    public String getName() {
        return name;
    }

    public ConditionSet getConditionSet() {
        return conditionSet;
    }

    public boolean supportsCondition(final Condition condition) {
        Objects.requireNonNull(condition);
        if (conditionSet == null) {
            return false;
        } else {
            return conditionSet.supportsCondition(condition);
        }
    }

    public String getDocRefType() {
        return docRefType;
    }

    public Boolean getQueryable() {
        return queryable;
    }

    public boolean queryable() {
        return queryable != null && queryable;
    }

    @JsonIgnore
    public boolean isNumeric() {
        return getFieldType().isNumeric();
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public int compareTo(final Field field) {
        return name.compareTo(field.getName());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryField)) {
            return false;
        }
        final QueryField that = (QueryField) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
