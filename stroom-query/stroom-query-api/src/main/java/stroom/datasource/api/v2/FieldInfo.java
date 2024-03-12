package stroom.datasource.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FieldInfo implements Field {

    @JsonProperty
    private final String fldName;
    @JsonProperty
    private final FieldType fldType;
    @JsonProperty
    private final ConditionSet conditions;
    @JsonProperty
    private final String docRefType;
    @JsonProperty
    private final Boolean queryable;

    @JsonCreator
    public FieldInfo(@JsonProperty("fldName") final String fldName,
                     @JsonProperty("fldType") final FieldType fldType,
                     @JsonProperty("conditions") final ConditionSet conditions,
                     @JsonProperty("docRefType") final String docRefType,
                     @JsonProperty("queryable") final Boolean queryable) {
        this.fldName = fldName;
        this.fldType = fldType;
        this.conditions = conditions;
        this.docRefType = docRefType;
        this.queryable = queryable;
    }

    public static FieldInfo create(final QueryField field) {
        return builder()
                .fldName(field.getFldName())
                .fldType(field.getFldType())
                .conditions(field.getConditionSet())
                .docRefType(field.getDocRefType())
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

    public ConditionSet getConditions() {
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
        if (!(o instanceof FieldInfo)) {
            return false;
        }
        final FieldInfo that = (FieldInfo) o;
        return Objects.equals(fldName, that.fldName) && fldType == that.fldType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fldName, fldType);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String fldName;
        private FieldType fldType;
        private ConditionSet conditions;
        private String docRefType;
        private Boolean queryable;

        public Builder() {
        }

        public Builder(final FieldInfo fieldInfo) {
            this.fldName = fieldInfo.fldName;
            this.fldType = fieldInfo.fldType;
            this.conditions = fieldInfo.conditions;
            this.docRefType = fieldInfo.docRefType;
            this.queryable = fieldInfo.queryable;
        }

        public Builder fldName(final String fldName) {
            this.fldName = fldName;
            return this;
        }

        public Builder fldType(final FieldType fldType) {
            this.fldType = fldType;
            return this;
        }

        public Builder conditions(final ConditionSet conditions) {
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
                    fldName,
                    fldType,
                    conditions,
                    docRefType,
                    queryable);
        }
    }
}
