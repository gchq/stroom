package stroom.pipeline.shared;

import stroom.docref.DocRef;
import stroom.util.shared.HasUuid;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class XsltDTO extends DocRef implements HasUuid {
    @JsonProperty
    private final String description;
    @JsonProperty
    private final String data;

    public XsltDTO(final XsltDoc doc) {
        super(XsltDoc.DOCUMENT_TYPE, doc.getUuid(), doc.getName());
        data = doc.getData();
        description = doc.getDescription();
    }

    @JsonCreator
    public XsltDTO(@JsonProperty("type") final String type,
                   @JsonProperty("uuid") final String uuid,
                   @JsonProperty("name") final String name,
                   @JsonProperty("description") final String description,
                   @JsonProperty("data") final String data) {
        super(type, uuid, name);
        this.description = description;
        this.data = data;
    }

    public String getDescription() {
        return description;
    }

    public String getData() {
        return data;
    }
}
