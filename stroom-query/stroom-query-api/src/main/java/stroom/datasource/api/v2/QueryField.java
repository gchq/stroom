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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"name", "fieldType", "type", "docRefType", "queryable", "conditionSet"})
@JsonInclude(Include.NON_NULL)
public class QueryField implements Field, HasDisplayValue {

    @Deprecated
    @JsonProperty("type")
    private String typeName;

    @JsonProperty
    private final String name;
    @JsonProperty
    private final FieldType fieldType;
    @JsonProperty
    private final ConditionSet conditionSet;
    @JsonProperty
    private final String docRefType;
    @JsonProperty
    private final Boolean queryable;

    @JsonCreator
    public QueryField(final String name,
                      @JsonProperty("fieldType") final FieldType fieldType,
                      @Deprecated @JsonProperty("type") final String typeName,
                      final ConditionSet conditionSet,
                      final String docRefType,
                      final Boolean queryable) {
        this.name = name;
        this.fieldType = convertType(fieldType, typeName);
        this.conditionSet = conditionSet;
        this.docRefType = docRefType;
        this.queryable = queryable;
    }

    public static QueryField createId(final String name) {
        return createId(name, true);
    }

    public static QueryField createId(final String name,
                                      final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.ID)
                .conditionSet(ConditionSet.DEFAULT_ID)
                .queryable(queryable)
                .build();
    }

    public static QueryField createKeyword(final String name) {
        return createKeyword(name, true);
    }

    public static QueryField createKeyword(final String name,
                                           final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.KEYWORD)
                .conditionSet(ConditionSet.DEFAULT_KEYWORD)
                .queryable(queryable)
                .build();
    }

    public static QueryField createInteger(final String name) {
        return createInteger(name, true);
    }

    public static QueryField createInteger(final String name,
                                           final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.INTEGER)
                .conditionSet(ConditionSet.DEFAULT_NUMERIC)
                .queryable(queryable)
                .build();
    }

    public static QueryField createLong(final String name) {
        return createLong(name, true);
    }

    public static QueryField createLong(final String name,
                                        final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.LONG)
                .conditionSet(ConditionSet.DEFAULT_NUMERIC)
                .queryable(queryable)
                .build();
    }

    public static QueryField createFloat(final String name) {
        return createFloat(name, true);
    }

    public static QueryField createFloat(final String name,
                                         final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.FLOAT)
                .conditionSet(ConditionSet.DEFAULT_NUMERIC)
                .queryable(queryable)
                .build();
    }

    public static QueryField createDouble(final String name) {
        return createDouble(name, true);
    }

    public static QueryField createDouble(final String name,
                                          final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.DOUBLE)
                .conditionSet(ConditionSet.DEFAULT_NUMERIC)
                .queryable(queryable)
                .build();
    }

    public static QueryField createIpV4Address(final String name) {
        return createIpV4Address(name, true);
    }

    public static QueryField createIpV4Address(final String name,
                                               final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.IPV4_ADDRESS)
                .conditionSet(ConditionSet.DEFAULT_NUMERIC)
                .queryable(queryable)
                .build();
    }

    public static QueryField createBoolean(final String name) {
        return createBoolean(name, true);
    }

    public static QueryField createBoolean(final String name,
                                           final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.BOOLEAN)
                .conditionSet(ConditionSet.DEFAULT_BOOLEAN)
                .queryable(queryable)
                .build();
    }

    public static QueryField createDate(final String name) {
        return createDate(name, true);
    }

    public static QueryField createDate(final String name,
                                        final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.DATE)
                .conditionSet(ConditionSet.DEFAULT_DATE)
                .queryable(queryable)
                .build();
    }

    public static QueryField createText(final String name) {
        return createText(name, true);
    }

    public static QueryField createText(final String name,
                                        final Boolean queryable) {
        return builder()
                .name(name)
                .fieldType(FieldType.TEXT)
                .conditionSet(ConditionSet.DEFAULT_TEXT)
                .queryable(queryable)
                .build();
    }



















    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are unique, allowing
     * the name to be used as the value in expression terms.
     */
    public static QueryField byUniqueName(final String docRefType,
                                           final String name) {
        return builder()
                .name(name)
                .fieldType(FieldType.DOC_REF)
                .conditionSet(ConditionSet.DOC_REF_ALL)
                .docRefType(docRefType)
                .queryable(Boolean.TRUE)
                .build();
    }

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} name is used as the value in expression terms, accepting
     * that name=x may match >1 docrefs.
     */
    public static QueryField byNonUniqueName(final String docRefType,
                                              final String name) {
        return builder()
                .name(name)
                .fieldType(FieldType.DOC_REF)
                .conditionSet(ConditionSet.DOC_REF_NAME)
                .docRefType(docRefType)
                .queryable(Boolean.TRUE)
                .build();
    }

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} uuid is used as the value in expression terms for a unique
     * match. Other conditions are not supported as that would require the user to enter uuids,
     * and it is not clear in the UI whether they are dealing in UUIDs or names.
     */
    public static QueryField byUuid(final String docRefType,
                                     final String name) {
        return builder()
                .name(name)
                .fieldType(FieldType.DOC_REF)
                .conditionSet(ConditionSet.DOC_REF_UUID)
                .docRefType(docRefType)
                .queryable(Boolean.TRUE)
                .build();
    }







    private FieldType convertType(final FieldType fieldType, final String typeName) {
        if (fieldType == null) {
            if (typeName != null) {
                switch (typeName) {
                    case "Id": {
                        return FieldType.ID;
                    }
                    case "Boolean": {
                        return FieldType.BOOLEAN;
                    }
                    case "Integer": {
                        return FieldType.INTEGER;
                    }
                    case "Long": {
                        return FieldType.LONG;
                    }
                    case "Float": {
                        return FieldType.FLOAT;
                    }
                    case "Double": {
                        return FieldType.DOUBLE;
                    }
                    case "Date": {
                        return FieldType.DATE;
                    }
                    case "Text": {
                        return FieldType.TEXT;
                    }
                    case "Keyword": {
                        return FieldType.KEYWORD;
                    }
                    case "IpV4Address": {
                        return FieldType.IPV4_ADDRESS;
                    }
                    case "DocRef": {
                        return FieldType.DOC_REF;
                    }
                }
            }
            return FieldType.TEXT;
        }
        return fieldType;
    }

    @Override
    public String getName() {
        return name;
    }

    public FieldType getFieldType() {
        return fieldType;
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private FieldType fieldType;
        private ConditionSet conditionSet;
        private String docRefType;
        private Boolean queryable;

        private Builder() {
        }

        private Builder(final QueryField queryField) {
            this.name = queryField.name;
            this.fieldType = queryField.fieldType;
            this.conditionSet = queryField.conditionSet;
            this.docRefType = queryField.docRefType;
            this.queryable = queryField.queryable;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder fieldType(final FieldType fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        public Builder conditionSet(final ConditionSet conditionSet) {
            this.conditionSet = conditionSet;
            return this;
        }

        public Builder docRefType(final String docRefType) {
            this.docRefType = docRefType;
            return this;
        }

        public Builder queryable(final Boolean queryable) {
            this.queryable = queryable;
            return this;
        }

        public QueryField build() {
            if (conditionSet == null && fieldType != null) {
                conditionSet = ConditionSet.getDefault(fieldType);
            }
            return new QueryField(name, fieldType, null, conditionSet, docRefType, queryable);
        }
    }
}
