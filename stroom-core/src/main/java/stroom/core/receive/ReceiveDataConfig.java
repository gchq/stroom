package stroom.core.receive;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig extends AbstractConfig implements IsStroomConfig {

    private final String receiptPolicyUuid;
    private final String unknownClassification;
    private final String feedNamePattern;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        unknownClassification = "UNKNOWN CLASSIFICATION";
        feedNamePattern = "^[A-Z0-9_-]{3,}$";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
                             @JsonProperty("unknownClassification") final String unknownClassification,
                             @JsonProperty("feedNamePattern") final String feedNamePattern) {
        this.receiptPolicyUuid = receiptPolicyUuid;
        this.unknownClassification = unknownClassification;
        this.feedNamePattern = feedNamePattern;
    }

    @JsonPropertyDescription("The UUID of the data receipt policy to use")
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
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
                ", unknownClassification='" + unknownClassification + '\'' +
                ", feedNamePattern='" + feedNamePattern + '\'' +
                '}';
    }
}
