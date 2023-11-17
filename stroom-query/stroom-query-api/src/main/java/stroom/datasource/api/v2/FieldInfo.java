package stroom.datasource.api.v2;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FieldInfo implements HasDisplayValue {

    @JsonProperty
    private final FieldType fieldType;
    @JsonProperty
    private final String fieldName;
    @JsonProperty
    private final Conditions conditions;
    @JsonProperty
    private final String docRefType;
    @JsonProperty
    private final Boolean queryable;

    @JsonCreator
    public FieldInfo(@JsonProperty("fieldType") final FieldType fieldType,
                     @JsonProperty("fieldName") final String fieldName,
                     @JsonProperty("conditions") final Conditions conditions,
                     @JsonProperty("docRefType") final String docRefType,
                     @JsonProperty("queryable") final Boolean queryable) {
        this.fieldType = fieldType;
        this.fieldName = fieldName;
        this.conditions = conditions;
        this.docRefType = docRefType;
        this.queryable = queryable;
    }

    public static FieldInfo create(final QueryField field) {
        return builder()
                .fieldType(field.getFieldType())
                .fieldName(field.getName())
                .conditions(field.getConditions())
                .docRefType(field.getDocRefType())
                .build();
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Conditions getConditions() {
        return conditions;
    }

    public String getDocRefType() {
        return docRefType;
    }

    public Boolean getQueryable() {
        return queryable;
    }

    public boolean queryable() {
        return queryable == null || queryable;
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
        if (!(o instanceof FieldInfo)) {
            return false;
        }
        final FieldInfo that = (FieldInfo) o;
        return fieldType == that.fieldType &&
                Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldType, fieldName);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private FieldType fieldType;
        private String fieldName;
        private Conditions conditions;
        private String docRefType;
        private Boolean queryable;

        public Builder() {
        }

        public Builder(final FieldInfo fieldInfo) {
            this.fieldType = fieldInfo.fieldType;
            this.fieldName = fieldInfo.fieldName;
            this.conditions = fieldInfo.conditions;
            this.docRefType = fieldInfo.docRefType;
            this.queryable = fieldInfo.queryable;
        }

        public Builder fieldType(final FieldType fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        public Builder fieldName(final String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder conditions(final Conditions conditions) {
            this.conditions = conditions;
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

        public FieldInfo build() {
            return new FieldInfo(
                    fieldType,
                    fieldName,
                    conditions,
                    docRefType,
                    queryable);
        }
    }
}
