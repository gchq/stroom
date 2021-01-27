package stroom.dictionary.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DictionaryDTO extends DocRef {
    @JsonProperty
    private final String description;
    @JsonProperty
    private final String data;
    @JsonProperty
    private final List<DocRef> imports;

    public DictionaryDTO(final DictionaryDoc doc) {
        super(DictionaryDoc.DOCUMENT_TYPE, doc.getUuid(), doc.getName());
        this.description = doc.getDescription();
        this.data = doc.getData();
        this.imports = doc.getImports();
    }

    @JsonCreator
    public DictionaryDTO(@JsonProperty("type") final String type,
                         @JsonProperty("uuid") final String uuid,
                         @JsonProperty("name") final String name,
                         @JsonProperty("description") final String description,
                         @JsonProperty("data") final String data,
                         @JsonProperty("imports") final List<DocRef> imports) {
        super(type, uuid, name);
        this.description = description;
        this.data = data;
        this.imports = imports;
    }

    public String getDescription() {
        return description;
    }

    public String getData() {
        return data;
    }

    public List<DocRef> getImports() {
        return imports;
    }
}
