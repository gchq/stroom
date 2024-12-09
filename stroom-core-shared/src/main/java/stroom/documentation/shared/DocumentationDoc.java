package stroom.documentation.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.HasData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    public static final String DOCUMENT_TYPE = "Documentation";
    public static final SvgImage ICON = SvgImage.DOCUMENT_DOCUMENTATION;

    @JsonProperty
    private String documentation;
    @JsonProperty
    private String data;

    public DocumentationDoc() {
    }

    @JsonCreator
    public DocumentationDoc(@JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("uniqueName") final String uniqueName,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("documentation") final String documentation,
                            @JsonProperty("data") final String data) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.documentation = documentation;
        this.data = data;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
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
