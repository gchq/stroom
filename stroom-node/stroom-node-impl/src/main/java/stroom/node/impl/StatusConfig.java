package stroom.node.impl;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

public class StatusConfig extends AbstractConfig {

    private HeapHistogramConfig heapHistogramConfig = new HeapHistogramConfig();

    @JsonProperty("heapHistogram")
    public HeapHistogramConfig getHeapHistogramConfig() {
        return heapHistogramConfig;
    }

    public void setHeapHistogramConfig(final HeapHistogramConfig heapHistogramConfig) {
        this.heapHistogramConfig = heapHistogramConfig;
    }

    @Override
    public String toString() {
        return "StatusConfig{" +
                "heapHistogramConfig=" + heapHistogramConfig +
                '}';
    }
}
