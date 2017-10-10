package stroom.proxy.handler;

import org.codehaus.jackson.annotate.JsonProperty;

public class LogRequestConfig {
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
