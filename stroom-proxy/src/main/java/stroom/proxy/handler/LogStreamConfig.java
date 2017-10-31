package stroom.proxy.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LogStreamConfig {
    private String metaKeys = "guid,feed,system,environment,remotehost,remoteaddress";

    /**
     * Optional log line with header attributes output as defined by this property
     */
    @JsonProperty
    public String getMetaKeys() {
        return metaKeys;
    }

    @JsonProperty
    public void setMetaKeys(final String metaKeys) {
        this.metaKeys = metaKeys;
    }
}
