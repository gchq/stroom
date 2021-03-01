package stroom.proxy.app.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class ProxyRequestConfig {

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
