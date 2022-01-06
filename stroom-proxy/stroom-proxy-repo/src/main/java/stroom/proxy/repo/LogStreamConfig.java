package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class LogStreamConfig extends AbstractConfig implements IsProxyConfig {

    private final String metaKeys;

    public LogStreamConfig() {
        metaKeys = "guid,feed,system,environment,remotehost,remoteaddress,remotedn,remotecertexpiry";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public LogStreamConfig(@JsonProperty("metaKeys") final String metaKeys) {
        this.metaKeys = metaKeys;
    }

    /**
     * Optional log line with header attributes output as defined by this property
     */
    @JsonProperty
    public String getMetaKeys() {
        return metaKeys;
    }
}
