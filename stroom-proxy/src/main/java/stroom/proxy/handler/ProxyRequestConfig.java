package stroom.proxy.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProxyRequestConfig {
    private String feedStatusUrl;
    private String receiptPolicyUuid;

    @JsonProperty
    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

    @JsonProperty
    public void setFeedStatusUrl(final String feedStatusUrl) {
        this.feedStatusUrl = feedStatusUrl;
    }

    @JsonProperty
    public String getReceiptPolicyUuid() {
        return receiptPolicyUuid;
    }

    @JsonProperty
    public void setReceiptPolicyUuid(final String receiptPolicyUuid) {
        this.receiptPolicyUuid = receiptPolicyUuid;
    }
}
