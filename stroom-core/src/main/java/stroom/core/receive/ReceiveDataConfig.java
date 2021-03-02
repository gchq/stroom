package stroom.core.receive;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ReceiveDataConfig extends AbstractConfig {

    private String receiptPolicyUuid;
    private String unknownClassification = "UNKNOWN CLASSIFICATION";
    private String feedNamePattern = "^[A-Z0-9_-]{3,}$";

    @JsonPropertyDescription("The UUID of the data receipt policy to use")
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }

    @SuppressWarnings("unused")
    public void setReceiptPolicyUuid(final String receiptPolicyUuid) {
        this.receiptPolicyUuid = receiptPolicyUuid;
    }

    @JsonPropertyDescription("The classification banner to display for data if one is not defined")
    public String getUnknownClassification() {
        return unknownClassification;
    }

    @SuppressWarnings("unused")
    public void setUnknownClassification(final String unknownClassification) {
        this.unknownClassification = unknownClassification;
    }

    @ValidRegex
    @JsonPropertyDescription("The regex pattern for feed names")
    public String getFeedNamePattern() {
        return feedNamePattern;
    }

    @SuppressWarnings("unused")
    public void setFeedNamePattern(final String feedNamePattern) {
        this.feedNamePattern = feedNamePattern;
    }

    @Override
    public String toString() {
        return "DataFeedConfig{" +
                "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
                ", unknownClassification='" + unknownClassification + '\'' +
                ", feedNamePattern='" + feedNamePattern + '\'' +
                '}';
    }
}
