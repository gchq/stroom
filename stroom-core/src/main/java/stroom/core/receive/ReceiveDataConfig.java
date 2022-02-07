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
    private final boolean requireTokenAuthentication;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        unknownClassification = "UNKNOWN CLASSIFICATION";
        feedNamePattern = "^[A-Z0-9_-]{3,}$";
        requireTokenAuthentication = false;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
                             @JsonProperty("unknownClassification") final String unknownClassification,
                             @JsonProperty("feedNamePattern") final String feedNamePattern,
                             @JsonProperty("requireTokenAuthentication") final boolean requireTokenAuthentication) {
        this.receiptPolicyUuid = receiptPolicyUuid;
        this.unknownClassification = unknownClassification;
        this.feedNamePattern = feedNamePattern;
        this.requireTokenAuthentication = requireTokenAuthentication;
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

    @JsonPropertyDescription("Require token authentication to send data to Stroom")
    public boolean isRequireTokenAuthentication() {
        return requireTokenAuthentication;
    }

    @Override
    public String toString() {
        return "ReceiveDataConfig{" +
                "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
                ", unknownClassification='" + unknownClassification + '\'' +
                ", feedNamePattern='" + feedNamePattern + '\'' +
                ", requireTokenAuthentication=" + requireTokenAuthentication +
                '}';
    }
}
