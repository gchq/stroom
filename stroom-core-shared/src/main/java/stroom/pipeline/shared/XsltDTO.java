package stroom.pipeline.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class XsltDTO extends DocRef {
    @JsonProperty
    private String description;
    @JsonProperty
    private String data;

    public XsltDTO() {
    }

    public XsltDTO(final XsltDoc doc) {
        super(XsltDoc.DOCUMENT_TYPE, doc.getUuid(), doc.getName());
        setData(doc.getData());
        setDescription(doc.getDescription());
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

    public void setDescription(String description) {
        this.description = description;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
