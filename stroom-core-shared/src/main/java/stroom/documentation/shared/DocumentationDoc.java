package stroom.documentation.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.HasData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description("A Document type for simply storing user created documentation, e.g. adding a Documentation document " +
             "into a folder to describe the contents of that folder.")
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DocumentationDoc extends AbstractDoc implements HasData {

    public static final String TYPE = "Documentation";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.DOCUMENTATION_DOCUMENT_TYPE;

    @JsonProperty
    private String documentation;
    @JsonProperty
    private String data;

    @JsonCreator
    public DocumentationDoc(@JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("documentation") final String documentation,
                            @JsonProperty("data") final String data) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.documentation = documentation;
        this.data = data;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final DocumentationDoc that = (DocumentationDoc) o;
        return Objects.equals(documentation, that.documentation)
               && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), documentation, data);
    }


    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDoc.AbstractBuilder<DocumentationDoc, DocumentationDoc.Builder> {

        private String documentation;
        private String data;

        private Builder() {
        }

        private Builder(final DocumentationDoc documentationDoc) {
            super(documentationDoc);
            this.documentation = documentationDoc.documentation;
            this.data = documentationDoc.data;
        }

        public Builder documentation(final String documentation) {
            this.documentation = documentation;
            return self();
        }

        public Builder data(final String data) {
            this.data = data;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public DocumentationDoc build() {
            return new DocumentationDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    documentation,
                    data);
        }
    }
}
