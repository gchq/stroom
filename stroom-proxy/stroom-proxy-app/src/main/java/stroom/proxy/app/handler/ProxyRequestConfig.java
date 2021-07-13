package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.AbstractProxyConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({"storingEnabled", "repoDir", "format", "rollCron"})
public class ProxyRequestConfig extends AbstractConfig implements IsProxyConfig {

    private String receiptPolicyUuid;

    @JsonProperty
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }

    @JsonProperty
    public void setReceiptPolicyUuid(final String receiptPolicyUuid) {
        this.receiptPolicyUuid = receiptPolicyUuid;
    }
}
