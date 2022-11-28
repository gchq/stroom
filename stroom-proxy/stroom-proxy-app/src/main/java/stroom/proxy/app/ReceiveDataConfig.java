package stroom.proxy.app;

import stroom.data.shared.StreamTypeNames;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.IsSupersetOf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class ReceiveDataConfig extends AbstractConfig implements IsProxyConfig {

    private final String receiptPolicyUuid;
    private final Set<String> metaTypes;
    private final boolean requireTokenAuthentication;

    public ReceiveDataConfig() {
        receiptPolicyUuid = null;
        metaTypes = new HashSet<>(StreamTypeNames.ALL_HARD_CODED_STREAM_TYPE_NAMES);
        requireTokenAuthentication = false;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ReceiveDataConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
                             @JsonProperty("metaTypes") final Set<String> metaTypes,
                             @JsonProperty("requireTokenAuthentication") final boolean requireTokenAuthentication) {
        this.receiptPolicyUuid = receiptPolicyUuid;
        this.metaTypes = metaTypes;
        this.requireTokenAuthentication = requireTokenAuthentication;
    }

    @JsonPropertyDescription("The UUID of the data receipt policy to use. If not set it will fall back to checking " +
            "the feed status on the downstream stroom or stroom-proxy.")
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
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
    }) // List should contain, as a minimum. all those types that the java code reference
    public Set<String> getMetaTypes() {
        return metaTypes;
    }

    @JsonPropertyDescription("Require token authentication when receiving data.")
    public boolean isRequireTokenAuthentication() {
        return requireTokenAuthentication;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReceiveDataConfig that = (ReceiveDataConfig) o;
        return requireTokenAuthentication == that.requireTokenAuthentication && Objects.equals(receiptPolicyUuid,
                that.receiptPolicyUuid) && Objects.equals(metaTypes, that.metaTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptPolicyUuid, metaTypes, requireTokenAuthentication);
    }

    @Override
    public String toString() {
        return "ReceiveDataConfig{" +
                "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
                ", metaTypes=" + metaTypes +
                ", requireTokenAuthentication=" + requireTokenAuthentication +
                '}';
    }
}
