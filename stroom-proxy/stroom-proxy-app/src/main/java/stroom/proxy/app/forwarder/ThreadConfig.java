package stroom.proxy.app.forwarder;

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

    private final int examineSourceThreadCount;
    private final int forwardThreadCount;
    private final int forwardRetryThreadCount;

    public ThreadConfig() {
        forwardRetryThreadCount = 2;
        forwardThreadCount = 10;
        examineSourceThreadCount = 3;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ThreadConfig(@JsonProperty("examineSourceThreadCount") final int examineSourceThreadCount,
                        @JsonProperty("forwardThreadCount") final int forwardThreadCount,
                        @JsonProperty("forwardRetryThreadCount") final int forwardRetryThreadCount) {
        this.examineSourceThreadCount = examineSourceThreadCount;
        this.forwardThreadCount = forwardThreadCount;
        this.forwardRetryThreadCount = forwardRetryThreadCount;
    }

    @RequiresProxyRestart
    @Min(0)
    @JsonProperty
    public int getExamineSourceThreadCount() {
        return examineSourceThreadCount;
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
