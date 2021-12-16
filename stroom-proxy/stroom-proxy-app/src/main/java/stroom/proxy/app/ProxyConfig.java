package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ReceiptPolicyConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.RepoDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.AssertTrue;

@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig extends AbstractConfig implements IsProxyConfig {

    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("proxyConfig");

    public static final String PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = "haltBootOnConfigValidationFailure";

    private final boolean useDefaultOpenIdCredentials;
    private final boolean haltBootOnConfigValidationFailure;
    private final String contentDir;

    private final ProxyPathConfig pathConfig;
    private final RepoDbConfig proxyDbConfig;
    private final ReceiptPolicyConfig receiptPolicyConfig;
    private final ProxyRepoConfig proxyRepoConfig;
    private final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig;
    private final AggregatorConfig aggregatorConfig;
    private final ForwarderConfig forwarderConfig;
    private final LogStreamConfig logStreamConfig;
    private final ContentSyncConfig contentSyncConfig;
    private final FeedStatusConfig feedStatusConfig;
    private final RestClientConfig restClientConfig;
    private final ThreadConfig threadConfig;

    public ProxyConfig() {
        useDefaultOpenIdCredentials = true;
        haltBootOnConfigValidationFailure = true;
        contentDir = null;

        pathConfig = new ProxyPathConfig();
        proxyDbConfig = new RepoDbConfig();
        receiptPolicyConfig = new ReceiptPolicyConfig();
        proxyRepoConfig = new ProxyRepoConfig();
        proxyRepoFileScannerConfig = new ProxyRepoFileScannerConfig();
        aggregatorConfig = new AggregatorConfig();
        forwarderConfig = new ForwarderConfig();
        logStreamConfig = new LogStreamConfig();
        contentSyncConfig = new ContentSyncConfig();
        feedStatusConfig = new FeedStatusConfig();
        restClientConfig = new RestClientConfig();
        threadConfig = new ThreadConfig();
    }

    ADD_CTOR

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
    public String getContentDir() {
        return contentDir;
    }

    @JsonProperty("path")
    public ProxyPathConfig getPathConfig() {
        return pathConfig;
    }

    @JsonProperty("db")
    public RepoDbConfig getProxyDbConfig() {
        return proxyDbConfig;
    }

    @JsonProperty("receiptPolicy")
    public ReceiptPolicyConfig getReceiptPolicyConfig() {
        return receiptPolicyConfig;
    }

    @JsonProperty("repository")
    public ProxyRepoConfig getProxyRepositoryConfig() {
        return proxyRepoConfig;
    }


    @JsonProperty("scanner")
    public ProxyRepoFileScannerConfig getProxyRepoFileScannerConfig() {
        return proxyRepoFileScannerConfig;
    }

    @JsonProperty("aggregator")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    @JsonProperty("forwarder")
    public ForwarderConfig getForwarderConfig() {
        return forwarderConfig;
    }

    @JsonProperty("logStream")
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty("contentSync")
    public ContentSyncConfig getContentSyncConfig() {
        return contentSyncConfig;
    }

    @JsonProperty("feedStatus")
    public FeedStatusConfig getFeedStatusConfig() {
        return feedStatusConfig;
    }

    @JsonProperty("restClient")
    public RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }

    @JsonProperty("threads")
    public ThreadConfig getThreadConfig() {
        return threadConfig;
    }
}
