package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class LogStreamConfig extends AbstractConfig implements IsProxyConfig {

    private String metaKeys = "guid,feed,system,environment,remotehost,remoteaddress,remotedn,remotecertexpiry";

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
