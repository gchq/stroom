package stroom.proxy.app.handler;

import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class LogStreamConfig implements IsProxyConfig {

    private String metaKeys = "guid,feed,system,environment,remotehost,remoteaddress,remotedn";

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
