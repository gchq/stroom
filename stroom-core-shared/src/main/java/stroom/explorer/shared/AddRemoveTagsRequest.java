package stroom.explorer.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class AddRemoveTagsRequest {

    @JsonProperty
    private final List<DocRef> docRefs;
    @JsonProperty
    private final Set<String> tags;

    @JsonCreator
    public AddRemoveTagsRequest(@JsonProperty("docRefs") final List<DocRef> docRefs,
                                @JsonProperty("tags") final Set<String> tags) {
        this.tags = tags;
        this.docRefs = docRefs;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    public Set<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return "AddRemoveTagsRequest{" +
                "docRefs=" + docRefs +
                ", tags=" + tags +
                '}';
    }
}
