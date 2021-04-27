package stroom.proxy.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "metaKeys"
})
public class LogStreamConfig {

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
