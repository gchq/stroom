package stroom.proxy.app.handler;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public class ThreadConfig extends AbstractConfig implements IsProxyConfig {

    private final int aggregateInputQueueThreadCount;
    private final int preAggregateInputQueueThreadCount;
    private final int forwardingInputQueueThreadCount;

    public ThreadConfig() {
        aggregateInputQueueThreadCount = 1;
        preAggregateInputQueueThreadCount = 1;
        forwardingInputQueueThreadCount = 1;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ThreadConfig(
            @JsonProperty("aggregateInputQueueThreadCount") final int aggregateInputQueueThreadCount,
            @JsonProperty("preAggregateInputQueueThreadCount") final int preAggregateInputQueueThreadCount,
            @JsonProperty("forwardingInputQueueThreadCount") final int forwardingInputQueueThreadCount) {

        this.aggregateInputQueueThreadCount = aggregateInputQueueThreadCount;
        this.preAggregateInputQueueThreadCount = preAggregateInputQueueThreadCount;
        this.forwardingInputQueueThreadCount = forwardingInputQueueThreadCount;
    }

    @JsonPropertyDescription("Number of threads to consume from the aggregate input queue.")
    @RequiresProxyRestart
    @Min(1)
    @JsonProperty
    public int getAggregateInputQueueThreadCount() {
        return aggregateInputQueueThreadCount;
    }

    @JsonPropertyDescription("Number of threads to consume from the pre-aggregate input queue.")
    @RequiresProxyRestart
    @Min(1)
    @JsonProperty
    public int getPreAggregateInputQueueThreadCount() {
        return preAggregateInputQueueThreadCount;
    }

    @JsonPropertyDescription("Number of threads to consume from the forwarding input queue.")
    @RequiresProxyRestart
    @Min(1)
    @JsonProperty
    public int getForwardingInputQueueThreadCount() {
        return forwardingInputQueueThreadCount;
    }

    @Override
    public String toString() {
        return "ThreadConfig{" +
               "aggregateInputQueueThreadCount=" + aggregateInputQueueThreadCount +
               ", preAggregateInputQueueThreadCount=" + preAggregateInputQueueThreadCount +
               ", forwardingInputQueueThreadCount=" + forwardingInputQueueThreadCount +
               '}';
    }
}
