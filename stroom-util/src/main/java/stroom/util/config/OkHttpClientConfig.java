/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.config;

import stroom.util.cert.SSLConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * An abstraction of some of the configuration available on OkHttpClient.Builder
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
@Deprecated // Clients should use HttpClientConfiguration
public class OkHttpClientConfig {

    @JsonProperty
    private final SSLConfig sslConfig;
    @JsonProperty
    private final List<String> httpProtocols;
    @JsonProperty
    private final StroomDuration callTimeout;
    @JsonProperty
    private final StroomDuration connectionTimeout;
    @JsonProperty
    private final StroomDuration readTimeout;
    @JsonProperty
    private final StroomDuration writeTimeout;
    @JsonProperty
    private final Boolean followRedirects;
    @JsonProperty
    private final Boolean followSslRedirects;
    @JsonProperty
    private final Boolean retryOnConnectionFailure;

    @JsonCreator
    public OkHttpClientConfig(@JsonProperty("httpProtocols") final List<String> httpProtocols,
                              @JsonProperty("sslConfig") final SSLConfig sslConfig,
                              @JsonProperty("callTimeout") final StroomDuration callTimeout,
                              @JsonProperty("connectionTimeout") final StroomDuration connectionTimeout,
                              @JsonProperty("readTimeout") final StroomDuration readTimeout,
                              @JsonProperty("writeTimeout") final StroomDuration writeTimeout,
                              @JsonProperty("followRedirects") final Boolean followRedirects,
                              @JsonProperty("followSslRedirects") final Boolean followSslRedirects,
                              @JsonProperty("retryOnConnectionFailure") final Boolean retryOnConnectionFailure) {
        this.httpProtocols = httpProtocols;
        this.sslConfig = sslConfig;
        this.callTimeout = callTimeout;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.followRedirects = followRedirects;
        this.followSslRedirects = followSslRedirects;
        this.retryOnConnectionFailure = retryOnConnectionFailure;
    }

    @JsonPropertyDescription("SSL configuration for the http client.")
    public SSLConfig getSslConfig() {
        return sslConfig;
    }

    @JsonPropertyDescription("An ordered list of http protocol versions to use, in order of preference. " +
            "E.g. 'http/1.1', 'http/2'. If not set the client will use the most efficient protocol available. " +
            "This may be useful if the server does not properly support http/2 or you are using two-way SSL.")
    public List<String> getHttpProtocols() {
        return httpProtocols;
    }

    @JsonPropertyDescription("Sets the default timeout for complete calls. A value of 0 means no timeout. " +
            "The call timeout spans the entire call: resolving DNS, connecting, writing the request body, server " +
            "processing, and reading the response body. If the call requires redirects or retries all must complete " +
            "within one timeout period. " +
            "If not set, the default value is 0 which imposes no timeout.")
    public StroomDuration getCallTimeout() {
        return callTimeout;
    }

    @JsonPropertyDescription("Sets the default connect timeout for new connections. A value of 0 means no timeout. " +
            "The connect timeout is applied when connecting a TCP socket to the target host. " +
            "If not set, the default value is 10 seconds.")
    public StroomDuration getConnectionTimeout() {
        return connectionTimeout;
    }

    @JsonPropertyDescription("Sets the default read timeout for new connections. A value of 0 means no timeout. " +
            "The read timeout is applied to both the TCP socket and for individual read IO operations including " +
            "on Source of the Response. " +
            "If not set the default value is 10 seconds.")
    public StroomDuration getReadTimeout() {
        return readTimeout;
    }

    @JsonPropertyDescription("Sets the default write timeout for new connections. A value of 0 means no timeout. " +
            "The write timeout is applied for individual write IO operations. " +
            "If not set the default value is 10 seconds.")
    public StroomDuration getWriteTimeout() {
        return writeTimeout;
    }

    @JsonPropertyDescription("Configure this client to follow redirects. " +
            "If unset, redirects will be followed.")
    public Boolean isFollowRedirects() {
        return followRedirects;
    }

    @JsonPropertyDescription("Configure this client to allow protocol redirects from HTTPS to HTTP and from " +
            "HTTP to HTTPS. Redirects are still first restricted by followRedirects. Defaults to true.")
    public Boolean isFollowSslRedirects() {
        return followSslRedirects;
    }

    @JsonPropertyDescription("""
            Configure this client to retry or not when a connectivity problem is encountered. By default, \
            this client silently recovers from the following problems:
            Unreachable IP addresses. If the URL's host has multiple IP addresses, failure to reach any individual \
            IP address doesn't fail the overall request. This can increase availability of multi-homed services.
            Stale pooled connections. The ConnectionPool reuses sockets to decrease request latency, but these \
            connections will occasionally time out.
            Unreachable proxy servers. A ProxySelector can be used to attempt multiple proxy servers in sequence, \
            eventually falling back to a direct connection.
            Set this to false to avoid retrying requests when doing so is destructive. In this case the calling \
            application should do its own recovery of connectivity failures.""")
    public Boolean isRetryOnConnectionFailure() {
        return retryOnConnectionFailure;
    }

    @Override
    public String toString() {
        return "OkHttpClientConfig{" +
                "sslConfig=" + sslConfig +
                ", httpProtocols=" + httpProtocols +
                ", callTimeout=" + callTimeout +
                ", connectionTimeout=" + connectionTimeout +
                ", readTimeout=" + readTimeout +
                ", writeTimeout=" + writeTimeout +
                ", followRedirects=" + followRedirects +
                ", followSslRedirects=" + followSslRedirects +
                ", retryOnConnectionFailure=" + retryOnConnectionFailure +
                '}';
    }
}
