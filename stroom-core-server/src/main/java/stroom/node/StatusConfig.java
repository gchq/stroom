package stroom.node;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StatusConfig {
    private HeapHistogramConfig heapHistogramConfig;

    public StatusConfig() {
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
}
