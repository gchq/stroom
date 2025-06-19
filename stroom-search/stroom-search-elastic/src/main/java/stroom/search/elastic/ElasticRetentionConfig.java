package stroom.search.elastic;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticRetentionConfig extends AbstractConfig implements IsStroomConfig {

    private final long scrollSize;

    public ElasticRetentionConfig() {
        this.scrollSize = 10000L;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticRetentionConfig(@JsonProperty("scrollSize") final long scrollSize) {
        this.scrollSize = scrollSize;
    }

    @JsonPropertyDescription("Number of documents to delete in each scroll request")
    public long getScrollSize() {
        return scrollSize;
    }

    @Override
    public String toString() {
        return "ElasticRetentionConfig{" +
                "scrollSize=" + scrollSize +
                '}';
    }
}
