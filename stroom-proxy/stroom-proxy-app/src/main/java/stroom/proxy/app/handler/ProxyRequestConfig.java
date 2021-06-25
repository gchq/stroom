package stroom.proxy.app.handler;

import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class ProxyRequestConfig implements IsProxyConfig {

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
