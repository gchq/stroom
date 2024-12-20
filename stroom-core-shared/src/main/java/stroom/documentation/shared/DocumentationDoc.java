package stroom.documentation.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.svg.shared.SvgImage;
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
public class DocumentationDoc extends Doc implements HasData {

    public static final String TYPE = "Documentation";
    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.CONFIGURATION,
            TYPE,
            TYPE,
            SvgImage.DOCUMENT_DOCUMENTATION);

    @JsonProperty
    private String documentation;
    @JsonProperty
    private String data;

    public DocumentationDoc() {
    }

    @JsonCreator
    public DocumentationDoc(@JsonProperty("type") final String type,
                            @JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("documentation") final String documentation,
                            @JsonProperty("data") final String data) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
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
}
