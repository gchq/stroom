package stroom.node.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class StatusConfig extends AbstractConfig implements IsStroomConfig {

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
