package stroom.annotation.shared;

import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AnnotationGroup {

    public static final String TYPE = "AnnotationGroup";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ANNOTATION_GROUP_DOCUMENT_TYPE;

    @JsonProperty
    private final int id;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;

    @JsonCreator
    public AnnotationGroup(@JsonProperty("id") final int id,
                           @JsonProperty("uuid") final String uuid,
                           @JsonProperty("name") final String name) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
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
        final AnnotationGroup that = (AnnotationGroup) o;
        return id == that.id &&
               Objects.equals(uuid, that.uuid) &&
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuid, name);
    }

    @Override
    public String toString() {
        return "AnnotationGroup{" +
               "id=" + id +
               ", uuid='" + uuid + '\'' +
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

        private int id;
        private String uuid;
        private String name;

        public Builder() {
        }

        public Builder(final AnnotationGroup doc) {
            this.id = doc.id;
            this.uuid = doc.uuid;
            this.name = doc.name;
        }

        public Builder id(final int id) {
            this.id = id;
            return self();
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        protected Builder self() {
            return this;
        }

        public AnnotationGroup build() {
            return new AnnotationGroup(
                    id,
                    uuid,
                    name);
        }
    }
}
