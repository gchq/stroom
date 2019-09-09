package stroom.search.solr.shared;

import stroom.docref.SharedObject;

import java.util.List;

public class SolrSynchState implements SharedObject {
    private Long lastSynchronized;
    private List<String> messages;

    public SolrSynchState() {
    }

    public SolrSynchState(final Long lastSynchronized, final List<String> messages) {
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
