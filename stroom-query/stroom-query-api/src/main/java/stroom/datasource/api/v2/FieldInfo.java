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
    private final String name;
    @JsonProperty
    private final FieldType type;
    @JsonProperty
    private final ConditionSet conditions;
    @JsonProperty
    private final String docRefType;
    @JsonProperty
    private final Boolean queryable;

    @JsonCreator
    public FieldInfo(@JsonProperty("name") final String name,
                     @JsonProperty("type") final FieldType type,
                     @JsonProperty("conditions") final ConditionSet conditions,
                     @JsonProperty("docRefType") final String docRefType,
                     @JsonProperty("queryable") final Boolean queryable) {
        this.name = name;
        this.type = type;
        this.conditions = conditions;
        this.docRefType = docRefType;
        this.queryable = queryable;
    }

    public static FieldInfo create(final QueryField field) {
        return builder()
                .name(field.getName())
                .type(field.getType())
                .conditions(field.getConditionSet())
                .docRefType(field.getDocRefType())
                .build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FieldType getType() {
        return type;
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
        if (!(o instanceof FieldInfo)) {
            return false;
        }
        final FieldInfo that = (FieldInfo) o;
        return Objects.equals(name, that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private FieldType type;
        private ConditionSet conditions;
        private String docRefType;
        private Boolean queryable;

        public Builder() {
        }

        public Builder(final FieldInfo fieldInfo) {
            this.name = fieldInfo.name;
            this.type = fieldInfo.type;
            this.conditions = fieldInfo.conditions;
            this.docRefType = fieldInfo.docRefType;
            this.queryable = fieldInfo.queryable;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder type(final FieldType type) {
            this.type = type;
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
                    name,
                    type,
                    conditions,
                    docRefType,
                    queryable);
        }
    }
}
