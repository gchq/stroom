package stroom.proxy.app;

import stroom.security.shared.ApiKeyCheckResource;
import stroom.security.shared.ApiKeyResource;
import stroom.util.cache.CacheConfig;
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
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class DownstreamHostConfig extends UriConfig implements IsProxyConfig {

    public static final String DEFAULT_API_KEY_VERIFICATION_URL_PATH = ResourcePaths.buildAuthenticatedApiPath(
            ApiKeyResource.BASE_PATH,
            ApiKeyCheckResource.VERIFY_API_KEY_PATH_PART);

    public static final String PROP_NAME_API_KEY = "apiKey";
    public static final String DEFAULT_SCHEME = "https";
    public static final String PROP_NAME_API_KEY_VERIFICATION_URL = "apiKeyVerificationUrl";
    public static final String PROP_NAME_VERIFIED_KEYS_CACHE = "verifiedApiKeysCache";

    private final String apiKey;
    private final String apiKeyVerificationUrl;
    private final CacheConfig verifiedApiKeysCache;

    public DownstreamHostConfig() {
        super(DEFAULT_SCHEME, null, null, null);
        this.apiKey = null;
        this.apiKeyVerificationUrl = null;
        this.verifiedApiKeysCache = buildDefaultCacheConfig();
    }

    @JsonCreator
    public DownstreamHostConfig(
            @JsonProperty(UriConfig.PROP_NAME_SCHEME) final String scheme,
            @JsonProperty(UriConfig.PROP_NAME_HOSTNAME) final String hostname,
            @JsonProperty(UriConfig.PROP_NAME_PORT) final Integer port,
            @JsonProperty(UriConfig.PROP_NAME_PATH_PREFIX) final String pathPrefix,
            @JsonProperty(PROP_NAME_API_KEY) final String apiKey,
            @JsonProperty(PROP_NAME_API_KEY_VERIFICATION_URL) final String apiKeyVerificationUrl,
            @JsonProperty(PROP_NAME_VERIFIED_KEYS_CACHE) final CacheConfig verifiedApiKeysCache) {

        super(Objects.requireNonNullElse(scheme, DEFAULT_SCHEME),
                Objects.requireNonNull(hostname),
                port,
                pathPrefix);
        this.apiKey = apiKey;
        this.apiKeyVerificationUrl = apiKeyVerificationUrl;
        this.verifiedApiKeysCache = Objects.requireNonNullElseGet(
                verifiedApiKeysCache,
                DownstreamHostConfig::buildDefaultCacheConfig);
    }

    private static CacheConfig buildDefaultCacheConfig() {
        return CacheConfig
                .builder()
                .maximumSize(1_000L)
                .expireAfterWrite(StroomDuration.ofMinutes(30))
                .statisticsMode(CacheConfig.PROXY_DEFAULT_STATISTICS_MODE)
                .build();
    }

    @Override
    @NotBlank
    @JsonProperty(UriConfig.PROP_NAME_HOSTNAME)
    public String getHostname() {
        return super.getHostname();
    }

    @JsonPropertyDescription("The API Key to use authenticate with the downstream stroom/proxy.")
    @JsonProperty
    public String getApiKey() {
        return apiKey;
    }

    @JsonPropertyDescription("The path to use for verifying API keys. If not set the downstreamHost configuration " +
                             "will be combined with the default API path for the verification. This is only needed " +
                             "when identityProviderType is NO_IDP.")
    @JsonProperty
    public String getApiKeyVerificationUrl() {
        return apiKeyVerificationUrl;
    }

    @JsonProperty
    public CacheConfig getVerifiedApiKeysCache() {
        return verifiedApiKeysCache;
    }

    /**
     * @return The base URI without any path (other than the pathPrefix)
     */
    @JsonIgnore
    public String getBaseUri() {
        return super.toString();
    }

    /**
     * @return The base URI combined with path.
     */
    public String getUri(final String path) {
        final StringBuilder sb = new StringBuilder(super.asUri());
        if (NullSafe.isNonBlankString(path)) {
            if (!path.startsWith("/")) {
                sb.append("/");
            }
        }
        sb.append(path);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "DownstreamHostConfig{" +
               "apiKey='" + apiKey + '\'' +
               ", apiKeyVerificationPath='" + apiKeyVerificationUrl + '\'' +
               ", verifiedKeysCache='" + verifiedApiKeysCache + '\'' +
               '}';
    }
}
