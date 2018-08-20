package stroom.datafeed;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class DataFeedConfig {
    /**
     * Same size as JDK's Buffered Output Stream.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private String receiptPolicyUuid;
    private int bufferSize = DEFAULT_BUFFER_SIZE;
    private String unknownClassification = "UNKNOWN CLASSIFICATION";
    private String feedNamePattern = "^[A-Z0-9_-]{3,}$";

    @JsonPropertyDescription("The UUID of the data receipt policy to use")
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }

    public void setReceiptPolicyUuid(final String receiptPolicyUuid) {
        this.receiptPolicyUuid = receiptPolicyUuid;
    }

    @JsonPropertyDescription("If set the default buffer size to use")
    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(final int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @JsonPropertyDescription("The classification banner to display for data if one is not defined")
    public String getUnknownClassification() {
        return unknownClassification;
    }

    public void setUnknownClassification(final String unknownClassification) {
        this.unknownClassification = unknownClassification;
    }

    @JsonPropertyDescription("The regex pattern for feed names")
    public String getFeedNamePattern() {
        return feedNamePattern;
    }

    public void setFeedNamePattern(final String feedNamePattern) {
        this.feedNamePattern = feedNamePattern;
    }
}
