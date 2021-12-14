package stroom.node.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class StatusConfig extends AbstractConfig {

    private final HeapHistogramConfig heapHistogramConfig;

    public StatusConfig() {
        heapHistogramConfig = new HeapHistogramConfig();
    }

    @JsonCreator
    public StatusConfig(@JsonProperty("heapHistogram") final HeapHistogramConfig heapHistogramConfig) {
        this.heapHistogramConfig = heapHistogramConfig;
    }

    @JsonProperty("heapHistogram")
    public HeapHistogramConfig getHeapHistogramConfig() {
        return heapHistogramConfig;
    }

    @Override
    public String toString() {
        return "StatusConfig{" +
                "heapHistogramConfig=" + heapHistogramConfig +
                '}';
    }
}
