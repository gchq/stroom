package stroom.search.solr.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class SolrSynchState {
    @JsonProperty
    private final Long lastSynchronized;
    @JsonProperty
    private final List<String> messages;

    @JsonCreator
    public SolrSynchState(@JsonProperty("lastSynchronized") final Long lastSynchronized,
                          @JsonProperty("messages") final List<String> messages) {
        this.lastSynchronized = lastSynchronized;
        this.messages = messages;
    }

    public Long getLastSynchronized() {
        return lastSynchronized;
    }

    public List<String> getMessages() {
        return messages;
    }
}
