package stroom.proxy.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ForwardStreamConfig {
    private String forwardUrl;
    private Integer forwardTimeoutMs = 30000;
    private Integer forwardDelayMs;
    private Integer forwardChunkSize;

    /**
     * Optional The URL's to forward onto. This is pass-through mode if repoDir is not set
     */
    @JsonProperty
    public String getForwardUrl() {
        return forwardUrl;
    }

    @JsonProperty
    public void setForwardUrl(final String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

    /**
     * Time out when forwarding
     */
    @JsonProperty
    public Integer getForwardTimeoutMs() {
        return forwardTimeoutMs;
    }

    @JsonProperty
    public void setForwardTimeoutMs(final Integer forwardTimeoutMs) {
        this.forwardTimeoutMs = forwardTimeoutMs;
    }

    /**
     * Debug setting to add a delay
     */
    @JsonProperty
    public Integer getForwardDelayMs() {
        return forwardDelayMs;
    }

    @JsonProperty
    public void setForwardDelayMs(final Integer forwardDelayMs) {
        this.forwardDelayMs = forwardDelayMs;
    }

    /**
     * Chunk size to use over http(s) if not set requires buffer to be fully loaded into memory
     */
    @JsonProperty
    public Integer getForwardChunkSize() {
        return forwardChunkSize;
    }

    @JsonProperty
    public void setForwardChunkSize(final Integer forwardChunkSize) {
        this.forwardChunkSize = forwardChunkSize;
    }
}
