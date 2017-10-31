package stroom.proxy.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LogStreamConfig {
    private String logRequest = "guid,feed,system,environment,remotehost,remoteaddress";

    /**
     * Optional log line with header attributes output as defined by this property
     */
    @JsonProperty
    public String getLogRequest() {
        return logRequest;
    }

    @JsonProperty
    public void setLogRequest(final String logRequest) {
        this.logRequest = logRequest;
    }
}
