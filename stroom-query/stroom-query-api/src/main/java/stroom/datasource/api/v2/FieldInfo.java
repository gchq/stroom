package stroom.datasource.api.v2;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FieldInfo implements HasDisplayValue {

    public static final String FIELDS_ID = "fields";
    public static final String FIELDS_PARENT = FIELDS_ID + ".";

    @JsonProperty
    private final String id;
    @JsonProperty
    private final boolean hasChildren;
    @JsonProperty
    private final String title;
    @JsonProperty
    private final AbstractField field;

    @JsonCreator
    public FieldInfo(@JsonProperty("id") final String id,
                     @JsonProperty("hasChildren") final boolean hasChildren,
                     @JsonProperty("title") final String title,
                     @JsonProperty("field") final AbstractField field) {
        this.id = id;
        this.hasChildren = hasChildren;
        this.title = title;
        this.field = field;
    }

    public static FieldInfo create(final AbstractField field) {
        return new FieldInfo(FIELDS_PARENT + field.getName(), false, field.getName(), field);
    }

    public String getId() {
        return id;
    }

    public boolean isHasChildren() {
        return hasChildren;
    }

    public String getTitle() {
        return title;
    }

    public AbstractField getField() {
        return field;
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return title;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private boolean hasChildren;
        private String title;
        private AbstractField field;

        public Builder() {
        }

        public Builder(final FieldInfo fieldInfo) {
            this.id = fieldInfo.id;
            this.hasChildren = fieldInfo.hasChildren;
            this.title = fieldInfo.title;
            this.field = fieldInfo.field;
        }


        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder hasChildren(final boolean hasChildren) {
            this.hasChildren = hasChildren;
            return this;
        }

        public Builder title(final String title) {
            this.title = title;
            return this;
        }

        public Builder field(final AbstractField field) {
            this.field = field;
            return this;
        }

        public FieldInfo build() {
            return new FieldInfo(id, hasChildren, title, field);
        }
    }
}
