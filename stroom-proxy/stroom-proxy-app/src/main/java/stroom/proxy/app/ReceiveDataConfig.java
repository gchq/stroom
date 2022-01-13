package stroom.proxy.app;

import stroom.data.shared.StreamTypeNames;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.IsSupersetOf;
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig extends AbstractConfig implements IsStroomConfig {

    /**
     * Same size as JDK's Buffered Output Stream.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String receiptPolicyUuid;
    private final int bufferSize;
    private final String unknownClassification;
    private final String feedNamePattern;
    private final Set<String> metaTypes;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        bufferSize = DEFAULT_BUFFER_SIZE;
        unknownClassification = "UNKNOWN CLASSIFICATION";
        feedNamePattern = "^[A-Z0-9_-]{3,}$";
        metaTypes = new HashSet<>(StreamTypeNames.ALL_TYPE_NAMES);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
                             @JsonProperty("bufferSize") final int bufferSize,
                             @JsonProperty("unknownClassification") final String unknownClassification,
                             @JsonProperty("feedNamePattern") final String feedNamePattern,
                             @JsonProperty("metaTypes") final Set<String> metaTypes) {
        this.receiptPolicyUuid = receiptPolicyUuid;
        this.bufferSize = bufferSize;
        this.unknownClassification = unknownClassification;
        this.feedNamePattern = feedNamePattern;
        this.metaTypes = metaTypes;
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

    @NotNull
    @NotEmpty
    @JsonPropertyDescription("Set of supported meta type names. This set must contain all of the names " +
            "in the default value for this property but can contain additional names.")
    @IsSupersetOf(requiredValues = {
            StreamTypeNames.RAW_EVENTS,
            StreamTypeNames.RAW_REFERENCE,
            StreamTypeNames.EVENTS,
            StreamTypeNames.REFERENCE,
            StreamTypeNames.META,
            StreamTypeNames.ERROR,
            StreamTypeNames.CONTEXT,
    }) // List should contain as a minimum all all those types that the java code reference
    public Set<String> getMetaTypes() {
        return metaTypes;
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
