package stroom.node.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class StatusConfig extends IsConfig {
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
