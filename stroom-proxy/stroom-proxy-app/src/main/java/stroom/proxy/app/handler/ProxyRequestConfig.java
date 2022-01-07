package stroom.proxy.app.handler;

import stroom.data.shared.StreamTypeNames;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRequestConfig extends AbstractConfig implements IsProxyConfig {

    private final String receiptPolicyUuid;
    private final Set<String> metaTypes;

    public ProxyRequestConfig() {
        receiptPolicyUuid = null;
        metaTypes = new HashSet<>(StreamTypeNames.ALL_TYPE_NAMES);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxyRequestConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid,
                              @JsonProperty("metaTypes") final Set<String> metaTypes) {
        this.receiptPolicyUuid = receiptPolicyUuid;
        this.metaTypes = metaTypes;
    }

    @JsonProperty
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }

    @NotNull
    @NotEmpty
    @JsonPropertyDescription("Set of supported meta type names. This set must contain all of the names " +
            "in the default value for this property but can contain additional names.")
    public Set<String> getMetaTypes() {
        return metaTypes;
    }

    // See TODO comment at top of StreamTypeNames regarding why we have this
    @JsonIgnore
    @ValidationMethod(message = "metaTypes must contain at least all of the names in the default value for metaTypes. " +
            "All items must be non-null and not empty.")
    public boolean isMetaTypesSetValid() {
        return metaTypes != null
                && metaTypes.containsAll(StreamTypeNames.ALL_TYPE_NAMES)
                && metaTypes.stream()
                .allMatch(type ->
                        type != null && !type.trim().isEmpty());
    }
}
