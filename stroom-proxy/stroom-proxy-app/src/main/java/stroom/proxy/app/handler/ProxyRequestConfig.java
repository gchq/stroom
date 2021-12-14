package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRequestConfig extends AbstractConfig implements IsProxyConfig {

    private final String receiptPolicyUuid;

    public ProxyRequestConfig() {
        receiptPolicyUuid = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxyRequestConfig(@JsonProperty("receiptPolicyUuid") final String receiptPolicyUuid) {
        this.receiptPolicyUuid = receiptPolicyUuid;
    }

    @JsonProperty
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }
}
