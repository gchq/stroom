package stroom.proxy.app.forwarder;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ThreadConfig extends AbstractConfig implements IsProxyConfig {

    private int examineSourceThreadCount = 3;
    private int aggregatorThreadCount = 2;
    private int createForwardRecordThreadCount = 2;
    private int forwardThreadCount = 10;
    private int forwardRetryThreadCount = 2;

    @JsonProperty
    public int getExamineSourceThreadCount() {
        return examineSourceThreadCount;
    }

    @JsonProperty
    public void setExamineSourceThreadCount(final int examineSourceThreadCount) {
        this.examineSourceThreadCount = examineSourceThreadCount;
    }

    @JsonProperty
    public int getAggregatorThreadCount() {
        return aggregatorThreadCount;
    }

    @JsonProperty
    public void setAggregatorThreadCount(final int aggregatorThreadCount) {
        this.aggregatorThreadCount = aggregatorThreadCount;
    }

    @JsonProperty
    public int getCreateForwardRecordThreadCount() {
        return createForwardRecordThreadCount;
    }

    @JsonProperty
    public void setCreateForwardRecordThreadCount(final int createForwardRecordThreadCount) {
        this.createForwardRecordThreadCount = createForwardRecordThreadCount;
    }

    @JsonProperty
    public int getForwardThreadCount() {
        return forwardThreadCount;
    }

    @JsonProperty
    public void setForwardThreadCount(final int forwardThreadCount) {
        this.forwardThreadCount = forwardThreadCount;
    }

    @JsonProperty
    public int getForwardRetryThreadCount() {
        return forwardRetryThreadCount;
    }

    @JsonProperty
    public void setForwardRetryThreadCount(final int forwardRetryThreadCount) {
        this.forwardRetryThreadCount = forwardRetryThreadCount;
    }
}
