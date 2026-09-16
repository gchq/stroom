/*
 * Copyright 2017 Crown Copyright
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

import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.app.handler.Durability;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.ForwardS3Config;
import stroom.proxy.app.handler.ForwarderConfig;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.security.openid.api.IdpType;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig extends AbstractConfig implements IsProxyConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyConfig.class);

    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("proxyConfig");

    public static final String PROP_NAME_DURABILITY = "durability";
    public static final String PROP_NAME_PROXY_ID = "proxyId";
    public static final String PROP_NAME_CONTENT_DIR = "contentDir";
    public static final String PROP_NAME_DOWNSTREAM_HOST = "downstreamHost";
    public static final String PROP_NAME_PATH = "path";
    public static final String PROP_NAME_RECEIVE = "receive";
    public static final String PROP_NAME_RECEIPT_POLICY = "receiptPolicy";
    public static final String PROP_NAME_EVENT_STORE = "eventStore";
    public static final String PROP_NAME_DIR_SCANNER = "dirScanner";
    public static final String PROP_NAME_FORWARD_FILE_DESTINATIONS = "forwardFileDestinations";
    public static final String PROP_NAME_FORWARD_HTTP_DESTINATIONS = "forwardHttpDestinations";
    public static final String PROP_NAME_FORWARD_S3_DESTINATIONS = "forwardS3Destinations";
    public static final String PROP_NAME_LOG_STREAM = "logStream";
    public static final String PROP_NAME_FEED_STATUS = "feedStatus";
    public static final String PROP_NAME_SECURITY = "security";
    public static final String PROP_NAME_SQS_CONNECTORS = "sqsConnectors";
    public static final String PROP_NAME_PIPELINE = "pipeline";


    /** R1 requires power-loss durability; the cost of it is configurable, not the guarantee. */
    public static final Durability DEFAULT_DURABILITY = Durability.FULL;
    protected static final String DEFAULT_CONTENT_DIR = "content";

    private final Durability durability;
    private final String proxyId;
    private final String contentDir;

    private final ProxyPathConfig pathConfig;
    private final ReceiveDataConfig receiveDataConfig;
    private final ProxyReceiptPolicyConfig receiptPolicyConfig;
    private final DownstreamHostConfig downstreamHostConfig;
    private final EventStoreConfig eventStoreConfig;
    private final DirScannerConfig dirScannerConfig;
    private final List<ForwardFileConfig> forwardFileDestinations;
    private final List<ForwardHttpPostConfig> forwardHttpDestinations;
    private final List<ForwardS3Config> forwardS3Destinations;
    private final LogStreamConfig logStreamConfig;
    private final FeedStatusConfig feedStatusConfig;
    private final ProxySecurityConfig proxySecurityConfig;
    private final List<SqsConnectorConfig> sqsConnectors;
    private final ProxyPipelineConfig pipelineConfig;

    public ProxyConfig() {
        this(DEFAULT_DURABILITY,
                null,
                DEFAULT_CONTENT_DIR,
                new ProxyPathConfig(),
                new ReceiveDataConfig(),
                new ProxyReceiptPolicyConfig(),
                new DownstreamHostConfig(),
                new EventStoreConfig(),
                new DirScannerConfig(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new LogStreamConfig(),
                new FeedStatusConfig(),
                new ProxySecurityConfig(),
                new ArrayList<>(),
                new ProxyPipelineConfig());
    }

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public ProxyConfig(
            @JsonProperty(PROP_NAME_DURABILITY) final Durability durability,
            @JsonProperty(PROP_NAME_PROXY_ID) final String proxyId,
            @JsonProperty(PROP_NAME_CONTENT_DIR) final String contentDir,
            @JsonProperty(PROP_NAME_PATH) final ProxyPathConfig pathConfig,
            @JsonProperty(PROP_NAME_RECEIVE) final ReceiveDataConfig receiveDataConfig,
            @JsonProperty(PROP_NAME_RECEIPT_POLICY) final ProxyReceiptPolicyConfig receiptPolicyConfig,
            @JsonProperty(PROP_NAME_DOWNSTREAM_HOST) final DownstreamHostConfig downstreamHostConfig,
            @JsonProperty(PROP_NAME_EVENT_STORE) final EventStoreConfig eventStoreConfig,
            @JsonProperty(PROP_NAME_DIR_SCANNER) final DirScannerConfig dirScannerConfig,
            @JsonProperty(PROP_NAME_FORWARD_FILE_DESTINATIONS) final List<ForwardFileConfig> forwardFileDestinations,
            @JsonProperty(PROP_NAME_FORWARD_HTTP_DESTINATIONS) final List<ForwardHttpPostConfig> forwardHttpDestinations,
            @JsonProperty(PROP_NAME_FORWARD_S3_DESTINATIONS) final List<ForwardS3Config> forwardS3Destinations,
            @JsonProperty(PROP_NAME_LOG_STREAM) final LogStreamConfig logStreamConfig,
            @JsonProperty(PROP_NAME_FEED_STATUS) final FeedStatusConfig feedStatusConfig,
            @JsonProperty(PROP_NAME_SECURITY) final ProxySecurityConfig proxySecurityConfig,
            @JsonProperty(PROP_NAME_SQS_CONNECTORS) final List<SqsConnectorConfig> sqsConnectors,
            @JsonProperty(PROP_NAME_PIPELINE) final ProxyPipelineConfig pipelineConfig) {

        this.durability = Objects.requireNonNullElse(durability, DEFAULT_DURABILITY);
        this.proxyId = proxyId;
        this.contentDir = NullSafe.nonBlankStringElse(contentDir, DEFAULT_CONTENT_DIR);
        this.pathConfig = Objects.requireNonNullElseGet(pathConfig, ProxyPathConfig::new);
        this.receiveDataConfig = Objects.requireNonNullElseGet(receiveDataConfig, ReceiveDataConfig::new);
        this.receiptPolicyConfig = Objects.requireNonNullElseGet(receiptPolicyConfig, ProxyReceiptPolicyConfig::new);
        this.downstreamHostConfig = Objects.requireNonNullElseGet(downstreamHostConfig, DownstreamHostConfig::new);
        this.eventStoreConfig = Objects.requireNonNullElseGet(eventStoreConfig, EventStoreConfig::new);
        this.dirScannerConfig = Objects.requireNonNullElseGet(dirScannerConfig, DirScannerConfig::new);
        this.forwardFileDestinations = NullSafe.list(forwardFileDestinations);
        this.forwardHttpDestinations = NullSafe.list(forwardHttpDestinations);
        this.forwardS3Destinations = NullSafe.list(forwardS3Destinations);
        this.logStreamConfig = Objects.requireNonNullElseGet(logStreamConfig, LogStreamConfig::new);
        this.feedStatusConfig = Objects.requireNonNullElseGet(feedStatusConfig, FeedStatusConfig::new);
        this.proxySecurityConfig = Objects.requireNonNullElseGet(proxySecurityConfig, ProxySecurityConfig::new);
        this.sqsConnectors = NullSafe.list(sqsConnectors);
        // Explicit or fail: no pipeline block is an unconfigured pipeline for validation to report.
        this.pipelineConfig = Objects.requireNonNullElseGet(pipelineConfig, ProxyPipelineConfig::unconfigured);
    }

    @JsonProperty(PROP_NAME_DURABILITY)
    @JsonPropertyDescription(
            "How much the proxy forces to stable storage for queue messages, and for event-store " +
            "files before a receipt is answered, before treating a step as committed. FULL (the " +
            "default) forces the message " +
            "and the directory entry that publishes it - the only mode that does not depend on the " +
            "filesystem's own ordering. QUEUE_ONLY is the same for queues. FILESYSTEM forces nothing " +
            "and is correct only where the storage genuinely provides the ordering itself. File " +
            "stores are not governed by this: each has its own durability, defaulting by store type " +
            "(pipeline.fileStores.<name>.durability).")
    public Durability getDurability() {
        return durability;
    }

    @Pattern(regexp = ProxyId.PROXY_ID_REGEX)
    @JsonProperty
    @JsonPropertyDescription("The unique id for this proxy instance: letters, digits and '-' only, and " +
                             "unique within the whole chain of proxies. It is used in the receipt ID that " +
                             "is generated for each received stream, and as the producer id on every " +
                             "pipeline message this node publishes. If not set a Proxy ID will be generated " +
                             "and stored in the file 'proxy-id.txt'.")
    public String getProxyId() {
        return proxyId;
    }

    @RequiresProxyRestart
    @JsonProperty
    @JsonPropertyDescription("The directory the receipt policy rules, dictionaries and verified API keys " +
                             "fetched from the downstream are persisted in, so that they survive a restart " +
                             "and an unreachable downstream. Relative to path.home unless absolute.")
    public String getContentDir() {
        return contentDir;
    }

    @JsonProperty(PROP_NAME_PATH)
    public ProxyPathConfig getPathConfig() {
        return pathConfig;
    }

    @JsonProperty(PROP_NAME_RECEIVE)
    public ReceiveDataConfig getReceiveDataConfig() {
        return receiveDataConfig;
    }

    @JsonProperty(PROP_NAME_RECEIPT_POLICY)
    public ProxyReceiptPolicyConfig getReceiptPolicyConfig() {
        return receiptPolicyConfig;
    }

    @JsonProperty(PROP_NAME_DOWNSTREAM_HOST)
    public DownstreamHostConfig getDownstreamHostConfig() {
        return downstreamHostConfig;
    }

    @JsonProperty(PROP_NAME_EVENT_STORE)
    public EventStoreConfig getEventStoreConfig() {
        return eventStoreConfig;
    }

    @JsonProperty(PROP_NAME_DIR_SCANNER)
    public DirScannerConfig getDirScannerConfig() {
        return dirScannerConfig;
    }

    // @Valid is load-bearing on the config lists in this class. Bean validation does
    // not descend into a collection without it, and the proxy's recursive config
    // walker explicitly skips collections (PropertyUtil.walkObjectTree). Without it
    // every constraint inside ForwardFileConfig, ForwardHttpPostConfig and
    // SqsConnectorConfig is silently unenforced, and a destination missing a required
    // field starts cleanly then fails later at runtime.
    @Valid
    @RequiresProxyRestart
    @JsonProperty(PROP_NAME_FORWARD_FILE_DESTINATIONS)
    public List<ForwardFileConfig> getForwardFileDestinations() {
        return forwardFileDestinations;
    }

    @Valid
    @RequiresProxyRestart
    @JsonProperty(PROP_NAME_FORWARD_HTTP_DESTINATIONS)
    public List<ForwardHttpPostConfig> getForwardHttpDestinations() {
        return forwardHttpDestinations;
    }

    @Valid
    @RequiresProxyRestart
    @JsonProperty(PROP_NAME_FORWARD_S3_DESTINATIONS)
    public List<ForwardS3Config> getForwardS3Destinations() {
        return forwardS3Destinations;
    }

    @JsonIgnore
    public List<ForwarderConfig> getAllForwardDestinations() {
        return Stream.of(
                        forwardFileDestinations,
                        forwardHttpDestinations,
                        forwardS3Destinations)
                .filter(Objects::nonNull)
                .flatMap(NullSafe::stream)
                .filter(Objects::nonNull) // an empty list entry in YAML is a null item
                .collect(Collectors.toList());
    }

    @JsonProperty(PROP_NAME_LOG_STREAM)
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty(PROP_NAME_FEED_STATUS)
    public FeedStatusConfig getFeedStatusConfig() {
        return feedStatusConfig;
    }


    @JsonProperty(PROP_NAME_SECURITY)
    public ProxySecurityConfig getProxySecurityConfig() {
        return proxySecurityConfig;
    }

    @Valid
    @JsonPropertyDescription("Configurations for AWS SQS connectors used for the " +
                             "EventStore (not S3 event notifications)")
    @JsonProperty
    public List<SqsConnectorConfig> getSqsConnectors() {
        return sqsConnectors;
    }

    @JsonProperty(PROP_NAME_PIPELINE)
    public ProxyPipelineConfig getPipelineConfig() {
        return pipelineConfig;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "All forwarders must have unique names, ignoring case.")
    public boolean isForwardNamesValid() {
        final List<String> allNames = streamAllForwarders()
                .map(ForwarderConfig::getName)
                .filter(Objects::nonNull) // Null names get picked up elsewhere
                .map(String::toLowerCase)
                .sorted()
                .toList();
        final long distinctCount = allNames.stream()
                .distinct()
                .count();
        return distinctCount == allNames.size();
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "Only one forwarder (regardless of type) is permitted if any " +
                                "forwarder has instant=true. If you want to forward to multiple " +
                                "destinations you cannot use instant forwarding.")
    public boolean isInstantForwardingValid() {
        final long enabledInstantForwardersCount = streamAllForwarders()
                .filter(ForwarderConfig::isEnabled)
                .filter(ForwarderConfig::isInstant)
                .count();
        if (enabledInstantForwardersCount > 1) {
            // There can be only one!
            return false;
        } else if (enabledInstantForwardersCount == 1) {
            final long allEnabledForwardersCount = streamAllForwarders()
                    .filter(ForwarderConfig::isEnabled)
                    .count();
            // It must be the only enabled forwarder
            return allEnabledForwardersCount == 1;
        } else {
            // No instant forwarders so we don't care about the number of forwarders (here at least)
            return true;
        }
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "If receive.receiptCheckMode is set to RECEIPT_POLICY and " +
                                "security.authentication.openId.idpType is not set to EXTERNAL_IDP then " +
                                "downstreamHost.apiKey must be set.")
    public boolean isReceiptPolicyApiKeyValid() {
        final ReceiptCheckMode receiptCheckMode = NullSafe.get(
                getReceiveDataConfig(),
                ReceiveDataConfig::getReceiptCheckMode);
        final IdpType idpType = NullSafe.get(
                getProxySecurityConfig(),
                ProxySecurityConfig::getAuthenticationConfig,
                ProxyAuthenticationConfig::getOpenIdConfig,
                ProxyOpenIdConfig::getIdentityProviderType);

        if (idpType != IdpType.EXTERNAL_IDP && receiptCheckMode == ReceiptCheckMode.RECEIPT_POLICY) {
            final String apiKey = NullSafe.get(
                    getDownstreamHostConfig(),
                    DownstreamHostConfig::getApiKey);
            return NullSafe.isNonBlankString(apiKey);
        }
        return true;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message =
            "downstreamHost.enabled must be set to true if receiptCheckMode is RECEIPT_POLICY " +
            "or FEED_STATUS.")
    public boolean isDownstreamValid() {
        return !isDownstreamRequired()
               || NullSafe.getOrElse(
                getDownstreamHostConfig(),
                DownstreamHostConfig::isEnabled,
                false);
    }

    /**
     * A node that does not receive must not be fed. The directory scanner and the SQS connectors
     * hand everything they find to the receiver, and on such a node the receiver refuses; the
     * result would be every scanned zip in the failure directory and every SQS message quarantined.
     * Instant forwarding receives without the stage, so it is exempt.
     */
    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message =
            "pipeline.stages.receive.enabled is false but dirScanner is enabled or an sqsConnector is " +
            "configured. A node that does not receive must not scan a directory or poll SQS: disable " +
            "them on this node, or enable the receive stage.")
    public boolean isReceiveStageEnabledForSources() {
        final boolean instant = streamAllEnabledForwarders().anyMatch(ForwarderConfig::isInstant);
        if (instant) {
            return true;
        }
        final PipelineStagesConfig stages = pipelineConfig == null
                ? null
                : pipelineConfig.getStages();
        // An unstated stage is the pipeline validator's to report, not this rule's.
        final ReceiveStageConfig receive = stages != null
                                           && stages.getConfiguredStages().contains(PipelineStageName.RECEIVE)
                ? stages.getReceive()
                : null;
        final boolean receiveDisabled = receive != null && receive.isEnabledSpecified() && !receive.isEnabled();
        if (!receiveDisabled) {
            return true;
        }
        final boolean scanner = dirScannerConfig != null && dirScannerConfig.isEnabled();
        final boolean sqs = sqsConnectors != null && !sqsConnectors.isEmpty();
        return !scanner && !sqs;
    }

    /**
     * {@link #isDownstreamValid()} asserts only that the downstream is <em>enabled</em>, never that
     * it can be reached, and that is not enough to make the receipt check a check.
     * {@link DownstreamHostConfig}'s no-arg constructor gives {@code enabled = true} with a
     * {@code null} hostname, and {@link ReceiptCheckMode#getDefault()} is {@code FEED_STATUS}: a
     * proxy configured to check receipts but given no hostname would build a hostless URI, fail
     * every call, and admit every feed silently. A security control failing open is a hard
     * validation failure, not a warning.
     */
    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message =
            "downstreamHost.hostname must be set if receiptCheckMode is RECEIPT_POLICY or " +
            "FEED_STATUS, else the receipt check cannot reach the downstream and would admit " +
            "all data.")
    public boolean isDownstreamHostnameValid() {
        return !isDownstreamRequired()
               || NullSafe.isNonBlankString(NullSafe.get(
                getDownstreamHostConfig(),
                DownstreamHostConfig::getHostname));
    }

    /**
     * @return True if the receipt check mode means data admission depends on the downstream.
     */
    @JsonIgnore
    private boolean isDownstreamRequired() {
        final ReceiptCheckMode receiptCheckMode = NullSafe.get(
                getReceiveDataConfig(),
                ReceiveDataConfig::getReceiptCheckMode);
        return receiptCheckMode == ReceiptCheckMode.RECEIPT_POLICY
               || receiptCheckMode == ReceiptCheckMode.FEED_STATUS;
    }

    /**
     * @return A {@link Stream} of all forward destination config objects regardless of enabled
     * state.
     */
    public Stream<ForwarderConfig> streamAllForwarders() {
        return getAllForwardDestinations().stream();
    }

    /**
     * @return A {@link Stream} of all enabled forward destination config objects.
     */
    public Stream<ForwarderConfig> streamAllEnabledForwarders() {
        return streamAllForwarders()
                .filter(ForwarderConfig::isEnabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link PropertyPath} by merging parts onto the root {@link PropertyPath}
     */
    public static PropertyPath buildPath(final String... parts) {
        return ROOT_PROPERTY_PATH.merge(parts);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class Builder {

        private Durability durability = DEFAULT_DURABILITY;
        private String proxyId;
        private String contentDir = DEFAULT_CONTENT_DIR;

        private ProxyPathConfig pathConfig = new ProxyPathConfig();
        private ReceiveDataConfig receiveDataConfig = new ReceiveDataConfig();
        private ProxyReceiptPolicyConfig receiptPolicyConfig = new ProxyReceiptPolicyConfig();
        private DownstreamHostConfig downstreamHostConfig;
        private EventStoreConfig eventStoreConfig = new EventStoreConfig();
        private DirScannerConfig dirScannerConfig = new DirScannerConfig();
        private final List<ForwardFileConfig> forwardFileDestinations = new ArrayList<>();
        private final List<ForwardHttpPostConfig> forwardHttpDestinations = new ArrayList<>();
        private final List<ForwardS3Config> forwardS3Destinations = new ArrayList<>();
        private LogStreamConfig logStreamConfig = new LogStreamConfig();
        private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
        private ProxySecurityConfig proxySecurityConfig = new ProxySecurityConfig();
        private final List<SqsConnectorConfig> sqsConnectors = new ArrayList<>();
        private ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig();

        private Builder() {

        }

        public Builder durability(final Durability durability) {
            this.durability = durability;
            return this;
        }

        public Builder proxyId(final String proxyId) {
            this.proxyId = proxyId;
            return this;
        }

        public Builder contentDir(final String contentDir) {
            this.contentDir = contentDir;
            return this;
        }

        public Builder pathConfig(final ProxyPathConfig pathConfig) {
            this.pathConfig = pathConfig;
            return this;
        }

        public Builder receiveDataConfig(final ReceiveDataConfig receiveDataConfig) {
            this.receiveDataConfig = receiveDataConfig;
            return this;
        }

        public Builder receiptPolicyConfig(final ProxyReceiptPolicyConfig receiptPolicyConfig) {
            this.receiptPolicyConfig = receiptPolicyConfig;
            return this;
        }

        public Builder downstreamHostConfig(final DownstreamHostConfig downstreamHostConfig) {
            this.downstreamHostConfig = downstreamHostConfig;
            return this;
        }

        public Builder eventStoreConfig(final EventStoreConfig eventStoreConfig) {
            this.eventStoreConfig = eventStoreConfig;
            return this;
        }

        public Builder dirScannerConfig(final DirScannerConfig dirScannerConfig) {
            this.dirScannerConfig = dirScannerConfig;
            return this;
        }

        public Builder addForwardFileDestination(final ForwardFileConfig forwarderFileConfig) {
            this.forwardFileDestinations.add(forwarderFileConfig);
            return this;
        }

        public Builder forwardFileDestinations(final Collection<ForwardFileConfig> forwarderFileConfigs) {
            this.forwardFileDestinations.clear();
            if (forwarderFileConfigs != null) {
                this.forwardFileDestinations.addAll(forwarderFileConfigs);
            }
            return this;
        }

        public Builder addForwardHttpDestination(final ForwardHttpPostConfig forwarderHttpConfig) {
            this.forwardHttpDestinations.add(forwarderHttpConfig);
            return this;
        }

        public Builder forwardHttpDestinations(final Collection<ForwardHttpPostConfig> forwarderHttpConfigs) {
            this.forwardHttpDestinations.clear();
            if (forwarderHttpConfigs != null) {
                this.forwardHttpDestinations.addAll(forwarderHttpConfigs);
            }
            return this;
        }

        public Builder addForwardS3Destination(final ForwardS3Config forwarderS3Config) {
            this.forwardS3Destinations.add(forwarderS3Config);
            return this;
        }

        public Builder forwardS3Destinations(final Collection<ForwardS3Config> forwarderS3Configs) {
            this.forwardS3Destinations.clear();
            if (forwarderS3Configs != null) {
                this.forwardS3Destinations.addAll(forwarderS3Configs);
            }
            return this;
        }

        public Builder logStreamConfig(final LogStreamConfig logStreamConfig) {
            this.logStreamConfig = logStreamConfig;
            return this;
        }

        public Builder feedStatusConfig(final FeedStatusConfig feedStatusConfig) {
            this.feedStatusConfig = feedStatusConfig;
            return this;
        }


        public Builder securityConfig(final ProxySecurityConfig proxySecurityConfig) {
            this.proxySecurityConfig = proxySecurityConfig;
            return this;
        }

        public Builder addSqsConnector(final SqsConnectorConfig sqsConnector) {
            this.sqsConnectors.add(sqsConnector);
            return this;
        }

        public Builder pipelineConfig(final ProxyPipelineConfig pipelineConfig) {
            this.pipelineConfig = pipelineConfig;
            return this;
        }

        public ProxyConfig build() {
            return new ProxyConfig(
                    durability,
                    proxyId,
                    contentDir,
                    pathConfig,
                    receiveDataConfig,
                    receiptPolicyConfig,
                    downstreamHostConfig,
                    eventStoreConfig,
                    dirScannerConfig,
                    forwardFileDestinations,
                    forwardHttpDestinations,
                    forwardS3Destinations,
                    logStreamConfig,
                    feedStatusConfig,
                    proxySecurityConfig,
                    sqsConnectors,
                    pipelineConfig);
        }
    }
}
