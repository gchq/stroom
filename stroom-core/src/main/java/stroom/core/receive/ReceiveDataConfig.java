package stroom.core.receive;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


public class ReceiveDataConfig extends AbstractConfig {

    /**
     * Same size as JDK's Buffered Output Stream.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String receiptPolicyUuid;
    private final int bufferSize;
    private final String unknownClassification;
    private final String feedNamePattern;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        bufferSize = DEFAULT_BUFFER_SIZE;
        unknownClassification = "UNKNOWN CLASSIFICATION";
        feedNamePattern = "^[A-Z0-9_-]{3,}$";
    }

    public ReceiveDataConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
                             @JsonProperty("bufferSize") final int bufferSize,
                             @JsonProperty("unknownClassification") final String unknownClassification,
                             @JsonProperty("feedNamePattern") final String feedNamePattern) {
        this.receiptPolicyUuid = receiptPolicyUuid;
        this.bufferSize = bufferSize;
        this.unknownClassification = unknownClassification;
        this.feedNamePattern = feedNamePattern;
    }

    @JsonPropertyDescription("The UUID of the data receipt policy to use")
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("If set the default buffer size to use")
    public int getBufferSize() {
        return bufferSize;
    }

    @JsonPropertyDescription("The classification banner to display for data if one is not defined")
    public String getUnknownClassification() {
        return unknownClassification;
    }

    @ValidRegex
    @JsonPropertyDescription("The regex pattern for feed names")
    public String getFeedNamePattern() {
        return feedNamePattern;
    }

    @Override
    public String toString() {
        return "DataFeedConfig{" +
                "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
                ", bufferSize=" + bufferSize +
                ", unknownClassification='" + unknownClassification + '\'' +
                ", feedNamePattern='" + feedNamePattern + '\'' +
                '}';
    }
}
