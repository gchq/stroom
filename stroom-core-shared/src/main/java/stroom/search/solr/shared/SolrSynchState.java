package stroom.search.solr.shared;



import java.util.List;

public class SolrSynchState {
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
