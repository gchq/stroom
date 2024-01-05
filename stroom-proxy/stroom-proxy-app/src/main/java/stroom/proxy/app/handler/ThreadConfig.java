package stroom.proxy.app.handler;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import javax.validation.constraints.Min;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ThreadConfig extends AbstractConfig implements IsProxyConfig {

    private final int forwardThreadCount;
    private final int forwardRetryThreadCount;

    public ThreadConfig() {
        forwardRetryThreadCount = 1;
        forwardThreadCount = 5;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ThreadConfig(@JsonProperty("forwardThreadCount") final int forwardThreadCount,
                        @JsonProperty("forwardRetryThreadCount") final int forwardRetryThreadCount) {
        this.forwardThreadCount = forwardThreadCount;
        this.forwardRetryThreadCount = forwardRetryThreadCount;
    }

    @RequiresProxyRestart
    @Min(0)
    @JsonProperty
    public int getForwardThreadCount() {
        return forwardThreadCount;
    }

    @RequiresProxyRestart
    @Min(0)
    @JsonProperty
    public int getForwardRetryThreadCount() {
        return forwardRetryThreadCount;
    }
}
