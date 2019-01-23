package stroom.node.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.IsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StatusConfig implements IsConfig {
    private HeapHistogramConfig heapHistogramConfig;

    StatusConfig() {
        heapHistogramConfig = new HeapHistogramConfig();
    }

    @Inject
    public StatusConfig(final HeapHistogramConfig heapHistogramConfig) {
        this.heapHistogramConfig = heapHistogramConfig;
    }

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
