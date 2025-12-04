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

package stroom.proxy.app;

import stroom.security.shared.ApiKeyCheckResource;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.HashAlgorithm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.UriConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Configuration for the downstream (in datafeed flow terms) stroom/stroom-proxy instance.
 */
@JsonPropertyOrder(alphabetic = true)
public class DownstreamHostConfig extends UriConfig implements IsProxyConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DownstreamHostConfig.class);

    public static final String DEFAULT_API_KEY_VERIFICATION_URL_PATH = ResourcePaths.buildAuthenticatedApiPath(
            ApiKeyResource.BASE_PATH,
            ApiKeyCheckResource.VERIFY_API_KEY_PATH_PART);
    public static final HashAlgorithm DEFAULT_HASH_ALGORITHM = HashAlgorithm.SHA2_512;
    public static final StroomDuration DEFAULT_MAX_CACHED_KEY_AGE = StroomDuration.ofMinutes(10);
    public static final StroomDuration DEFAULT_MAX_PERSISTED_KEY_AGE = StroomDuration.ofDays(30);
    public static final StroomDuration DEFAULT_NO_FETCH_INTERVAL = StroomDuration.ofSeconds(30);

    public static final String PROP_NAME_API_KEY = "apiKey";
    public static final String DEFAULT_SCHEME = "https";
    public static final String PROP_NAME_API_KEY_VERIFICATION_URL = "apiKeyVerificationUrl";
    public static final String PROP_NAME_VERIFIED_KEYS_CACHE = "verifiedApiKeysCache";
    public static final boolean DEFAULT_ENABLED = true;

    private final boolean enabled;
    private final String apiKey;
    private final String apiKeyVerificationUrl;
    private final HashAlgorithm persistedKeysHashAlgorithm;
    private final StroomDuration maxCachedKeyAge;
    private final StroomDuration maxPersistedKeyAge;
    private final StroomDuration noFetchIntervalAfterFailure;

    public DownstreamHostConfig() {
        super(DEFAULT_SCHEME, null, null, null);
        this.enabled = DEFAULT_ENABLED;
        this.apiKey = null;
        this.apiKeyVerificationUrl = null;
        this.persistedKeysHashAlgorithm = DEFAULT_HASH_ALGORITHM;
        this.maxCachedKeyAge = DEFAULT_MAX_CACHED_KEY_AGE;
        this.maxPersistedKeyAge = DEFAULT_MAX_PERSISTED_KEY_AGE;
        this.noFetchIntervalAfterFailure = DEFAULT_NO_FETCH_INTERVAL;
    }

    @JsonCreator
    public DownstreamHostConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty(UriConfig.PROP_NAME_SCHEME) final String scheme,
            @JsonProperty(UriConfig.PROP_NAME_HOSTNAME) final String hostname,
            @JsonProperty(UriConfig.PROP_NAME_PORT) final Integer port,
            @JsonProperty(UriConfig.PROP_NAME_PATH_PREFIX) final String pathPrefix,
            @JsonProperty(PROP_NAME_API_KEY) final String apiKey,
            @JsonProperty(PROP_NAME_API_KEY_VERIFICATION_URL) final String apiKeyVerificationUrl,
            @JsonProperty("persistedKeysHashAlgorithm") final HashAlgorithm persistedKeysHashAlgorithm,
            @JsonProperty("maxCachedKeyAge") final StroomDuration maxCachedKeyAge,
            @JsonProperty("maxPersistedKeyAge") final StroomDuration maxPersistedKeyAge,
            @JsonProperty("noFetchIntervalAfterFailure") final StroomDuration noFetchIntervalAfterFailure) {

        super(Objects.requireNonNullElse(scheme, DEFAULT_SCHEME),
                Objects.requireNonNull(hostname),
                port,
                pathPrefix);
        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_ENABLED);
        this.apiKey = apiKey;
        this.apiKeyVerificationUrl = apiKeyVerificationUrl;
        this.persistedKeysHashAlgorithm = Objects.requireNonNullElse(
                persistedKeysHashAlgorithm, DEFAULT_HASH_ALGORITHM);
        this.maxCachedKeyAge = Objects.requireNonNullElse(maxCachedKeyAge, DEFAULT_MAX_CACHED_KEY_AGE);
        this.maxPersistedKeyAge = Objects.requireNonNullElse(maxPersistedKeyAge, DEFAULT_MAX_PERSISTED_KEY_AGE);
        this.noFetchIntervalAfterFailure = Objects.requireNonNullElse(
                noFetchIntervalAfterFailure, DEFAULT_NO_FETCH_INTERVAL);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder copy(final DownstreamHostConfig copy) {
        final Builder builder = new Builder();
        builder.scheme = copy.getScheme();
        builder.hostname = copy.getHostname();
        builder.port = copy.getPort();
        builder.pathPrefix = copy.getPathPrefix();
        builder.enabled = copy.isEnabled();
        builder.apiKey = copy.getApiKey();
        builder.apiKeyVerificationUrl = copy.getApiKeyVerificationUrl();
        builder.persistedKeysHashAlgorithm = copy.getPersistedKeysHashAlgorithm();
        builder.maxCachedKeyAge = copy.getMaxCachedKeyAge();
        builder.maxPersistedKeyAge = copy.getMaxPersistedKeyAge();
        builder.noFetchIntervalAfterFailure = copy.getNoFetchIntervalAfterFailure();
        return builder;
    }

    @JsonPropertyDescription("Whether this stroom-proxy has a downstream stroom/stroom-proxy instance " +
                             "to use for feed/API key/receipt poliocy checking. If this proxy is just used " +
                             "to forward to file only then set to false.")
    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @JsonProperty(UriConfig.PROP_NAME_HOSTNAME)
    public String getHostname() {
        return super.getHostname();
    }

    @JsonPropertyDescription("The API Key to use authenticate with the downstream stroom/proxy.")
    @JsonProperty
    public String getApiKey() {
        return apiKey;
    }

    @JsonPropertyDescription(
            "The URL/path to use for verifying API keys. " +
            "If not set the downstreamHost configuration will be combined with the default API " +
            "path (/api/apikey/v2/verifyApiKey)." +
            "If this property is not set, the downstreamHost configuration will be combined with the default API " +
            "path (/status). " +
            "If this property is just a path, it will be combined with the downstreamHost configuration. " +
            "Only set this property if you wish to use a non-default path " +
            "or you want to use a different host/port/scheme to that defined in downstreamHost. " +
            "This property is also only needed when identityProviderType is NO_IDP.")
    @JsonProperty
    public String getApiKeyVerificationUrl() {
        return apiKeyVerificationUrl;
    }

    /**
     * @return The base URI without any path (other than the pathPrefix)
     */
    @JsonIgnore
    public String getBaseUri() {
        return super.toString();
    }

    @JsonProperty
    public HashAlgorithm getPersistedKeysHashAlgorithm() {
        return persistedKeysHashAlgorithm;
    }

    @JsonProperty
    public StroomDuration getMaxCachedKeyAge() {
        return maxCachedKeyAge;
    }

    @JsonProperty
    public StroomDuration getMaxPersistedKeyAge() {
        return maxPersistedKeyAge;
    }

    @JsonProperty
    public StroomDuration getNoFetchIntervalAfterFailure() {
        return noFetchIntervalAfterFailure;
    }

    /**
     * @return The base URI combined with path.
     */
    public String createUri(final String path) {
        final String baseUri = super.asUri();
        final String trimmedPath = NullSafe.trim(path);
        final StringBuilder sb = new StringBuilder(baseUri);
        if (NullSafe.isNonBlankString(trimmedPath) && !trimmedPath.equals("/")) {
            if (!baseUri.endsWith("/") && !trimmedPath.startsWith("/")) {
                sb.append("/")
                        .append(trimmedPath);
            } else if (baseUri.endsWith("/")
                       && trimmedPath.startsWith("/")
                       && trimmedPath.length() > 1) {
                sb.append(trimmedPath.substring(1));
            } else {
                sb.append(trimmedPath);
            }
        }
        final String url = sb.toString();
        LOGGER.debug("createUri() - path: '{}', url: '{}'", path, url);
        return url;
    }

    /**
     * Returns fullUri if it is non-blank, else builds a URI
     * by this downstreamHost config with defaultPath.
     */
    public String createUri(final String urlOrPath,
                            final String defaultPath) {
        final String url;
        if (NullSafe.isNonBlankString(urlOrPath)) {
            final String trimmedUrlOrPath = urlOrPath.trim();
            if (trimmedUrlOrPath.startsWith("/")) {
                // Just a path, so append it to the downstream host
                url = createUri(trimmedUrlOrPath);
            } else {
                // Have to assume it is a full url with host/port/etc
                url = trimmedUrlOrPath;
            }
        } else {
            url = createUri(Objects.requireNonNull(defaultPath, "defaultPath must be supplied"));
        }
        LOGGER.debug("createUri() - urlOrPath: '{}', defaultPath: '{}', url: '{}'",
                urlOrPath, defaultPath, url);
        return url;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        if (!super.equals(object)) {
            return false;
        }
        final DownstreamHostConfig that = (DownstreamHostConfig) object;
        return enabled == that.enabled
               && Objects.equals(apiKey, that.apiKey)
               && Objects.equals(apiKeyVerificationUrl, that.apiKeyVerificationUrl)
               && persistedKeysHashAlgorithm == that.persistedKeysHashAlgorithm
               && Objects.equals(maxCachedKeyAge, that.maxCachedKeyAge)
               && Objects.equals(maxPersistedKeyAge, that.maxPersistedKeyAge)
               && Objects.equals(noFetchIntervalAfterFailure, that.noFetchIntervalAfterFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                enabled,
                apiKey,
                apiKeyVerificationUrl,
                persistedKeysHashAlgorithm,
                maxCachedKeyAge,
                maxPersistedKeyAge,
                noFetchIntervalAfterFailure);
    }

    @Override
    public String toString() {
        return "DownstreamHostConfig{" +
               "enabled=" + enabled +
               ", apiKey='" + apiKey + '\'' +
               ", apiKeyVerificationUrl='" + apiKeyVerificationUrl + '\'' +
               ", persistedKeysHashAlgorithm=" + persistedKeysHashAlgorithm +
               ", maxCachedKeyAge=" + maxCachedKeyAge +
               ", maxPersistedKeyAge=" + maxPersistedKeyAge +
               ", noFetchIntervalAfterFailure=" + noFetchIntervalAfterFailure +
               '}';
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String scheme;
        private String hostname;
        private Integer port;
        private String pathPrefix;
        private boolean enabled;
        private String apiKey;
        private String apiKeyVerificationUrl;
        private HashAlgorithm persistedKeysHashAlgorithm;
        private StroomDuration maxCachedKeyAge;
        private StroomDuration maxPersistedKeyAge;
        private StroomDuration noFetchIntervalAfterFailure;

        private Builder() {
        }

        public Builder withScheme(final String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder withHostname(final String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder withPort(final int port) {
            this.port = port;
            return this;
        }

        public Builder withPrefix(final String pathPrefix) {
            this.pathPrefix = pathPrefix;
            return this;
        }

        public Builder withEnabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withApiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder withApiKeyVerificationUrl(final String apiKeyVerificationUrl) {
            this.apiKeyVerificationUrl = apiKeyVerificationUrl;
            return this;
        }

        public Builder withPersistedKeysHashAlgorithm(final HashAlgorithm persistedKeysHashAlgorithm) {
            this.persistedKeysHashAlgorithm = persistedKeysHashAlgorithm;
            return this;
        }

        public Builder withMaxCachedKeyAge(final StroomDuration maxCachedKeyAge) {
            this.maxCachedKeyAge = maxCachedKeyAge;
            return this;
        }

        public Builder withMaxPersistedKeyAge(final StroomDuration maxPersistedKeyAge) {
            this.maxPersistedKeyAge = maxPersistedKeyAge;
            return this;
        }

        public Builder withNoFetchIntervalAfterFailure(final StroomDuration noFetchIntervalAfterFailure) {
            this.noFetchIntervalAfterFailure = noFetchIntervalAfterFailure;
            return this;
        }

        public DownstreamHostConfig build() {
            return new DownstreamHostConfig(
                    enabled,
                    scheme,
                    hostname,
                    port,
                    pathPrefix,
                    apiKey,
                    apiKeyVerificationUrl,
                    persistedKeysHashAlgorithm,
                    maxCachedKeyAge,
                    maxPersistedKeyAge,
                    noFetchIntervalAfterFailure);
        }
    }
}
