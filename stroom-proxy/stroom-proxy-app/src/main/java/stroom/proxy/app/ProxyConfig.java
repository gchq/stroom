package stroom.proxy.app;

import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ForwardStreamConfig;
import stroom.proxy.app.handler.LogStreamConfig;
import stroom.proxy.app.handler.ProxyRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.AssertTrue;

@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig extends AbstractConfig implements IsProxyConfig {

    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("proxyConfig");

    public static final String PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = "haltBootOnConfigValidationFailure";

    private final String proxyContentDir;
    private final boolean useDefaultOpenIdCredentials;
    private final boolean haltBootOnConfigValidationFailure;

    private final ContentSyncConfig contentSyncConfig;
    private final FeedStatusConfig feedStatusConfig;
    private final ForwardStreamConfig forwardStreamConfig;
    private final RestClientConfig restClientConfig;
    private final LogStreamConfig logStreamConfig;
    private final ProxyPathConfig pathConfig;
    private final ProxyRepositoryConfig proxyRepositoryConfig;
    private final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private final ProxyRequestConfig proxyRequestConfig;

    public ProxyConfig() {
        proxyContentDir = "content";
        useDefaultOpenIdCredentials = true;
        haltBootOnConfigValidationFailure = true;

        contentSyncConfig = new ContentSyncConfig();
        feedStatusConfig = new FeedStatusConfig();
        forwardStreamConfig = new ForwardStreamConfig();
        restClientConfig = new RestClientConfig();
        logStreamConfig = new LogStreamConfig();
        pathConfig = new ProxyPathConfig();
        proxyRepositoryConfig = new ProxyRepositoryConfig();
        proxyRepositoryReaderConfig = new ProxyRepositoryReaderConfig();
        proxyRequestConfig = new ProxyRequestConfig();
    }

    public ProxyConfig(
            @JsonProperty("proxyContentDir") final String proxyContentDir,
            @JsonProperty("useDefaultOpenIdCredentials") final boolean useDefaultOpenIdCredentials,
            @JsonProperty("haltBootOnConfigValidationFailure") final boolean haltBootOnConfigValidationFailure,
            @JsonProperty("contentSync") final ContentSyncConfig contentSyncConfig,
            @JsonProperty("feedStatus") final FeedStatusConfig feedStatusConfig,
            @JsonProperty("forwardStream") final ForwardStreamConfig forwardStreamConfig,
            @JsonProperty("restClient") final RestClientConfig restClientConfig,
            @JsonProperty("logStream") final LogStreamConfig logStreamConfig,
            @JsonProperty("path") final ProxyPathConfig pathConfig,
            @JsonProperty("proxyRepository") final ProxyRepositoryConfig proxyRepositoryConfig,
            @JsonProperty("proxyRepositoryReader") final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig,
            @JsonProperty("proxyRequest") final ProxyRequestConfig proxyRequestConfig) {

        this.proxyContentDir = proxyContentDir;
        this.useDefaultOpenIdCredentials = useDefaultOpenIdCredentials;
        this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
        this.contentSyncConfig = contentSyncConfig;
        this.feedStatusConfig = feedStatusConfig;
        this.forwardStreamConfig = forwardStreamConfig;
        this.restClientConfig = restClientConfig;
        this.logStreamConfig = logStreamConfig;
        this.pathConfig = pathConfig;
        this.proxyRepositoryConfig = proxyRepositoryConfig;
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.proxyRequestConfig = proxyRequestConfig;
    }

    @AssertTrue(
            message = "proxyConfig." + PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE + " is set to false. " +
                    "If there is invalid configuration the system may behave in unexpected ways. This setting is " +
                    "not advised.",
            payload = ValidationSeverity.Warning.class)
    @JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE)
    @JsonPropertyDescription("If true, Stroom-Proxy will halt on start up if any errors are found in the YAML " +
            "configuration file. If false, the errors will simply be logged. Setting this to false is not advised.")
    public boolean isHaltBootOnConfigValidationFailure() {
        return haltBootOnConfigValidationFailure;
    }

    @JsonProperty()
    @JsonPropertyDescription("If true, stroom will use a set of default authentication credentials to allow" +
            "API calls from stroom-proxy. For test or demonstration purposes only, set to false for production. " +
            "If API keys are set elsewhere in config then they will override this setting.")
    public boolean isUseDefaultOpenIdCredentials() {
        return useDefaultOpenIdCredentials;
    }

    @JsonProperty
    public ProxyRequestConfig getProxyRequestConfig() {
        return proxyRequestConfig;
    }

    @JsonProperty
    public ForwardStreamConfig getForwardStreamConfig() {
        return forwardStreamConfig;
    }

    @JsonProperty
    public ProxyRepositoryConfig getProxyRepositoryConfig() {
        return proxyRepositoryConfig;
    }

    @JsonProperty
    public ProxyRepositoryReaderConfig getProxyRepositoryReaderConfig() {
        return proxyRepositoryReaderConfig;
    }

    @JsonProperty
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty("path")
    public ProxyPathConfig getProxyPathConfig() {
        return pathConfig;
    }

    @JsonProperty
    public ContentSyncConfig getContentSyncConfig() {
        return contentSyncConfig;
    }

    @JsonProperty("feedStatus")
    public FeedStatusConfig getFeedStatusConfig() {
        return feedStatusConfig;
    }

    @JsonProperty
    public String getProxyContentDir() {
        return proxyContentDir;
    }

    @JsonProperty("restClient")
    public RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }
}

