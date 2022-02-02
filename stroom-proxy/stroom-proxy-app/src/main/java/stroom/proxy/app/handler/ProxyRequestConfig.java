package stroom.proxy.app.handler;

import stroom.data.shared.StreamTypeNames;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.validation.IsSupersetOf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRequestConfig extends AbstractConfig implements IsProxyConfig {

    private final String receiptPolicyUuid;
    private final Set<String> metaTypes;
    private final boolean requireTokenAuthentication;
    private final String publicKey;
    private final String clientId;

    public ProxyRequestConfig() {
        receiptPolicyUuid = null;
        metaTypes = new HashSet<>(StreamTypeNames.ALL_TYPE_NAMES);
        requireTokenAuthentication = false;
        publicKey = null;
        clientId = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxyRequestConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
                              @JsonProperty("metaTypes") final Set<String> metaTypes,
                              @JsonProperty("requireTokenAuthentication") final boolean requireTokenAuthentication,
                              @JsonProperty("publicKey") final String publicKey,
                              @JsonProperty("clientId") final String clientId) {
        this.receiptPolicyUuid = receiptPolicyUuid;
        this.metaTypes = metaTypes;
        this.requireTokenAuthentication = requireTokenAuthentication;
        this.publicKey = publicKey;
        this.clientId = clientId;
    }

    @JsonProperty
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
    }) // List should contain as a minimum all all those types that the java code reference
    public Set<String> getMetaTypes() {
        return metaTypes;
    }

    @JsonPropertyDescription("Require token authentication to send data to Stroom")
    public boolean isRequireTokenAuthentication() {
        return requireTokenAuthentication;
    }

    @JsonPropertyDescription("The public key to be used to verify authentication tokens")
    public String getPublicKey() {
        return publicKey;
    }

    @JsonPropertyDescription("The expected client id contained in the supplied token")
    public String getClientId() {
        return clientId;
    }

    @Override
    public String toString() {
        return "ProxyRequestConfig{" +
                "receiptPolicyUuid='" + receiptPolicyUuid + '\'' +
                ", metaTypes=" + metaTypes +
                ", requireTokenAuthentication=" + requireTokenAuthentication +
                ", publicKey='" + publicKey + '\'' +
                ", clientId='" + clientId + '\'' +
                '}';
    }
}
