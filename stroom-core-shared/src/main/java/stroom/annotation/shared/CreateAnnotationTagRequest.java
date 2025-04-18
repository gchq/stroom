package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CreateAnnotationTagRequest {

    @JsonProperty
    private final AnnotationTagType type;
    @JsonProperty
    private final String name;

    @JsonCreator
    public CreateAnnotationTagRequest(
            @JsonProperty("type") final AnnotationTagType type,
            @JsonProperty("name") final String name) {
        this.type = type;
        this.name = name;
    }

    public AnnotationTagType getType() {
        return type;
    }

    public String getName() {
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
        final CreateAnnotationTagRequest that = (CreateAnnotationTagRequest) o;
        return type == that.type &&
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return "CreateAnnotationTagRequest{" +
               "type=" + type +
               ", name='" + name + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private AnnotationTagType type;
        private String name;

        public Builder() {
        }

        public Builder(final CreateAnnotationTagRequest doc) {
            this.type = doc.type;
            this.name = doc.name;
        }


        public Builder type(final AnnotationTagType type) {
            this.type = type;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        protected Builder self() {
            return this;
        }

        public CreateAnnotationTagRequest build() {
            return new CreateAnnotationTagRequest(
                    type,
                    name);
        }
    }
}
