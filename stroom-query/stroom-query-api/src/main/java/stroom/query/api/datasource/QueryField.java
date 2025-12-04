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

package stroom.query.api.datasource;

import stroom.docref.HasDisplayValue;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@JsonPropertyOrder({
        "type", // deprecated
        "name", // deprecated

        "fldName",
        "fldType",
        "docRefType",
        "queryable",
        "conditionSet"
})
@JsonInclude(Include.NON_NULL)
public class QueryField implements Field, HasDisplayValue {

    @Deprecated
    @JsonProperty("type")
    private String type;
    @Deprecated
    @JsonProperty("name")
    private String name;

    @JsonProperty
    private final String fldName;
    @JsonProperty
    private final FieldType fldType;
    @JsonProperty
    private final ConditionSet conditionSet;
    @JsonProperty
    private final String docRefType;
    @JsonProperty
    private final Boolean queryable;

    @JsonCreator
    public QueryField(@Deprecated @JsonProperty("type") final String type,
                      @Deprecated @JsonProperty("name") final String name,
                      @JsonProperty("fldName") final String fldName,
                      @JsonProperty("fldType") final FieldType fldType,
                      @JsonProperty("conditionSet") final ConditionSet conditionSet,
                      @JsonProperty("docRefType") final String docRefType,
                      @JsonProperty("queryable") final Boolean queryable) {
        // Don't use NullSafe as this breaks some tests
        this.fldName = fldName != null
                ? fldName
                : name;
        this.fldType = convertLegacyType(fldType, type);
        this.conditionSet = conditionSet;
        this.docRefType = docRefType;
        this.queryable = queryable;
    }

    private FieldType convertLegacyType(final FieldType fieldType, final String type) {
        if (fieldType == null) {
            if (type != null) {
                switch (type) {
                    case "Id":
                        return FieldType.ID;
                    case "Boolean":
                        return FieldType.BOOLEAN;
                    case "Integer":
                        return FieldType.INTEGER;
                    case "Long":
                        return FieldType.LONG;
                    case "Float":
                        return FieldType.FLOAT;
                    case "Double":
                        return FieldType.DOUBLE;
                    case "Date":
                        return FieldType.DATE;
                    case "Text":
                        return FieldType.TEXT;
                    case "Keyword":
                        return FieldType.KEYWORD;
                    case "IpV4Address":
                        return FieldType.IPV4_ADDRESS;
                    case "DocRef":
                        return FieldType.DOC_REF;
                }
            }
            return FieldType.TEXT;
        }
        return fieldType;
    }

    public static QueryField createId(final String name) {
        return createId(name, true);
    }

    public static QueryField createId(final String name,
                                      final Boolean queryable) {
        return builder()
                .fldName(name)
                .fldType(FieldType.ID)
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
                .fldName(name)
                .fldType(FieldType.KEYWORD)
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
                .fldName(name)
                .fldType(FieldType.INTEGER)
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
                .fldName(name)
                .fldType(FieldType.LONG)
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
                .fldName(name)
                .fldType(FieldType.FLOAT)
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
                .fldName(name)
                .fldType(FieldType.DOUBLE)
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
                .fldName(name)
                .fldType(FieldType.IPV4_ADDRESS)
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
                .fldName(name)
                .fldType(FieldType.BOOLEAN)
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
                .fldName(name)
                .fldType(FieldType.DATE)
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
                .fldName(name)
                .fldType(FieldType.TEXT)
                .conditionSet(ConditionSet.DEFAULT_TEXT)
                .queryable(queryable)
                .build();
    }


    /**
     * A {@link QueryField} for a {@link stroom.docref.DocRef} type whose names are unique, allowing
     * the name to be used as the value in expression terms.
     */
    public static QueryField createDocRefByUniqueName(final String docRefType,
                                                      final String name) {
        return builder()
                .fldName(name)
                .fldType(FieldType.DOC_REF)
                .conditionSet(ConditionSet.DOC_REF_ALL)
                .docRefType(docRefType)
                .queryable(Boolean.TRUE)
                .build();
    }

    /**
     * A {@link QueryField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} name is used as the value in expression terms, accepting
     * that name=x may match >1 docrefs.
     */
    public static QueryField createDocRefByNonUniqueName(final String docRefType,
                                                         final String name) {
        return builder()
                .fldName(name)
                .fldType(FieldType.DOC_REF)
                .conditionSet(ConditionSet.DOC_REF_NAME)
                .docRefType(docRefType)
                .queryable(Boolean.TRUE)
                .build();
    }

    /**
     * A {@link QueryField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} uuid is used as the value in expression terms for a unique
     * match. Other conditions are not supported as that would require the user to enter uuids,
     * and it is not clear in the UI whether they are dealing in UUIDs or names.
     */
    public static QueryField createDocRefByUuid(final String docRefType,
                                                final String name) {
        return builder()
                .fldName(name)
                .fldType(FieldType.DOC_REF)
                .conditionSet(ConditionSet.DOC_REF_UUID)
                .docRefType(docRefType)
                .queryable(Boolean.TRUE)
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
        return getFldType().isNumeric();
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return fldName;
    }

    @Override
    public int compareTo(final Field field) {
        return fldName.compareTo(field.getFldName());
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
        return Objects.equals(fldName, that.fldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fldName);
    }

    @Override
    public String toString() {
        return fldName;
    }

    public static Map<String, QueryField> buildFieldMap(final QueryField... queryFields) {
        return NullSafe.stream(queryFields)
                .collect(Collectors.toMap(
                        QueryField::getFldName,
                        Function.identity()));
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private String fldName;
        private FieldType fldType;
        private ConditionSet conditionSet;
        private String docRefType;
        private Boolean queryable;

        private Builder() {
        }

        private Builder(final QueryField queryField) {
            this.fldName = queryField.fldName;
            this.fldType = queryField.fldType;
            this.conditionSet = queryField.conditionSet;
            this.docRefType = queryField.docRefType;
            this.queryable = queryField.queryable;
        }

        public Builder fldName(final String fldName) {
            this.fldName = fldName;
            return this;
        }

        public Builder fldType(final FieldType fldType) {
            this.fldType = fldType;
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
            if (conditionSet == null && fldType != null) {
                conditionSet = ConditionSet.getDefault(fldType);
            }
            return new QueryField(
                    null,
                    null,
                    fldName,
                    fldType,
                    conditionSet,
                    docRefType,
                    queryable);
        }
    }
}
