/*
 * Copyright 2024 Crown Copyright
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

package stroom.config.app;

import stroom.activity.impl.db.ActivityConfig;
import stroom.analytics.impl.AnalyticsConfig;
import stroom.annotation.impl.AnnotationConfig;
import stroom.aws.s3.impl.S3Config;
import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.cluster.api.ClusterConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.NodeUriConfig;
import stroom.config.common.PublicUriConfig;
import stroom.config.common.UiUriConfig;
import stroom.contentstore.impl.ContentStoreConfig;
import stroom.core.receive.AutoContentCreationConfig;
import stroom.credentials.api.CredentialsConfig;
import stroom.dashboard.impl.DashboardConfig;
import stroom.docstore.impl.db.DocStoreConfig;
import stroom.event.logging.impl.LoggingConfig;
import stroom.explorer.impl.ExplorerConfig;
import stroom.feed.impl.FeedConfig;
import stroom.gitrepo.api.GitRepoConfig;
import stroom.importexport.impl.ContentPackImportConfig;
import stroom.importexport.impl.ExportConfig;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexFieldDbConfig;
import stroom.index.impl.selection.VolumeConfig;
import stroom.job.impl.JobSystemConfig;
import stroom.kafka.impl.KafkaConfig;
import stroom.langchain.api.ChatMemoryConfig;
import stroom.lifecycle.impl.LifecycleConfig;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.node.impl.NodeConfig;
import stroom.pipeline.PipelineConfig;
import stroom.planb.impl.PlanBConfig;
import stroom.processor.impl.ProcessorConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.impl.StroomReceiptPolicyConfig;
import stroom.search.elastic.ElasticConfig;
import stroom.search.impl.SearchConfig;
import stroom.search.solr.SolrConfig;
import stroom.state.impl.StateConfig;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.util.io.StroomPathConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRootName;
import jakarta.validation.constraints.AssertTrue;

@JsonRootName(AppConfig.NAME)
@JsonPropertyOrder(alphabetic = true)
public class AppConfig extends AbstractConfig implements IsStroomConfig {

    public static final String NAME = "stroom";
    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts(NAME);

    public static final String ROOT_PROPERTY_NAME = "appConfig";

    public static final String PROP_NAME_ACTIVITY = "activity";
    public static final String PROP_NAME_ANNOTATION = "annotation";
    public static final String PROP_NAME_ANALYTICS = "analytics";
    public static final String PROP_NAME_CONTENT_STORE = "contentStore";
    public static final String PROP_NAME_AUTHENTICATION = "authentication";
    public static final String PROP_NAME_AUTO_CONTENT_CREATION = "autoContentCreation";
    public static final String PROP_NAME_BENCHMARK = "benchmark";
    public static final String PROP_NAME_BYTE_BUFFER_POOL = "byteBufferPool";
    public static final String PROP_NAME_CHAT_MEMORY = "chatMemory";
    public static final String PROP_NAME_CLUSTER = "cluster";
    public static final String PROP_NAME_CLUSTER_LOCK = "clusterLock";
    public static final String PROP_NAME_CLUSTER_TASK = "clusterTask";
    public static final String PROP_NAME_COMMON_DB_DETAILS = "commonDbDetails";
    public static final String PROP_NAME_CONTENT_PACK_IMPORT = "contentPackImport";
    public static final String PROP_NAME_CREDENTIALS = "credentials";
    public static final String PROP_NAME_CORE = "core";
    public static final String PROP_NAME_DASHBOARD = "dashboard";
    public static final String PROP_NAME_DATA = "data";
    public static final String PROP_NAME_DOCSTORE = "docstore";
    public static final String PROP_NAME_ELASTIC = "elastic";
    public static final String PROP_NAME_EXPLORER = "explorer";
    public static final String PROP_NAME_EXPORT = "export";
    public static final String PROP_NAME_FEED = "feed";
    public static final String PROP_NAME_GIT_REPO = "gitRepo";
    public static final String PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = "haltBootOnConfigValidationFailure";
    public static final String PROP_NAME_INDEX = "index";
    public static final String PROP_NAME_JOB = "job";
    public static final String PROP_NAME_KAFKA = "kafka";
    public static final String PROP_NAME_LIFECYCLE = "lifecycle";
    public static final String PROP_NAME_LMDB_LIBRARY = "lmdbLibrary";
    public static final String PROP_NAME_LOGGING = "logging";
    public static final String PROP_NAME_NODE = "node";
    public static final String PROP_NAME_NODE_URI = "nodeUri";
    public static final String PROP_NAME_PATH = "path";
    public static final String PROP_NAME_PIPELINE = "pipeline";
    public static final String PROP_NAME_PROCESSOR = "processor";
    public static final String PROP_NAME_PROPERTIES = "properties";
    public static final String PROP_NAME_QUERY_DATASOURCE = "queryDataSource";
    public static final String PROP_NAME_PUBLIC_URI = "publicUri";
    public static final String PROP_NAME_QUERY_HISTORY = "queryHistory";
    public static final String PROP_NAME_RECEIVE = "receive";
    public static final String PROP_NAME_RECEIPT_POLICY = "receiptPolicy";
    public static final String PROP_NAME_S3 = "s3";
    public static final String PROP_NAME_SEARCH = "search";
    public static final String PROP_NAME_SECURITY = "security";
    public static final String PROP_NAME_SESSION_COOKIE = "sessionCookie";
    public static final String PROP_NAME_SESSION = "session";
    public static final String PROP_NAME_SOLR = "solr";
    public static final String PROP_NAME_STATE = "state";
    public static final String PROP_NAME_PLANB = "planb";
    public static final String PROP_NAME_STATISTICS = "statistics";
    public static final String PROP_NAME_UI = "ui";
    public static final String PROP_NAME_UI_URI = "uiUri";
    public static final String PROP_NAME_VOLUMES = "volumes";

    private final boolean haltBootOnConfigValidationFailure;

    private final CrossModuleConfig crossModuleConfig;
    private final ActivityConfig activityConfig;
    private final AnalyticsConfig analyticsConfig;
    private final AnnotationConfig annotationConfig;
    private final ContentStoreConfig contentStoreConfig;
    private final AutoContentCreationConfig autoContentCreationConfig;
    private final ByteBufferPoolConfig byteBufferPoolConfig;
    private final ChatMemoryConfig chatMemoryConfig;
    private final ClusterConfig clusterConfig;
    private final ClusterLockConfig clusterLockConfig;
    private final CommonDbConfig commonDbConfig;
    private final ContentPackImportConfig contentPackImportConfig;
    private final CredentialsConfig credentialsConfig;
    private final DashboardConfig dashboardConfig;
    private final DataConfig dataConfig;
    private final DocStoreConfig docStoreConfig;
    private final ElasticConfig elasticConfig;
    private final ExplorerConfig explorerConfig;
    private final ExportConfig exportConfig;
    private final FeedConfig feedConfig;
    private final GitRepoConfig gitRepoConfig;
    private final IndexConfig indexConfig;
    private final JobSystemConfig jobSystemConfig;
    private final KafkaConfig kafkaConfig;
    private final LifecycleConfig lifecycleConfig;
    private final LmdbLibraryConfig lmdbLibraryConfig;
    private final LoggingConfig loggingConfig;
    private final NodeConfig nodeConfig;
    private final NodeUriConfig nodeUri;
    private final PipelineConfig pipelineConfig;
    private final ProcessorConfig processorConfig;
    private final PropertyServiceConfig propertyServiceConfig;
    private final PublicUriConfig publicUri;
    private final IndexFieldDbConfig queryDataSourceConfig;
    private final ReceiveDataConfig receiveDataConfig;
    private final StroomReceiptPolicyConfig receiptPolicyConfig;
    private final S3Config s3Config;
    private final SearchConfig searchConfig;
    private final SecurityConfig securityConfig;
    private final SessionCookieConfig sessionCookieConfig;
    private final SessionConfig sessionConfig;
    private final SolrConfig solrConfig;
    private final StateConfig stateConfig;
    private final PlanBConfig planBConfig;
    private final StatisticsConfig statisticsConfig;
    private final StoredQueryConfig storedQueryConfig;
    private final StroomPathConfig pathConfig;
    private final UiConfig uiConfig;
    private final UiUriConfig uiUri;
    private final VolumeConfig volumeConfig;

    /**
     * Will construct a full immutable AppConfig tree will ALL defaults set.
     */
    public AppConfig() {
        this(true,
                new CrossModuleConfig(),
                new ActivityConfig(),
                new AnalyticsConfig(),
                new AnnotationConfig(),
                new ContentStoreConfig(),
                new AutoContentCreationConfig(),
                new ByteBufferPoolConfig(),
                new ChatMemoryConfig(),
                new ClusterConfig(),
                new ClusterLockConfig(),
                new CommonDbConfig(),
                new ContentPackImportConfig(),
                new CredentialsConfig(),
                new DashboardConfig(),
                new DataConfig(),
                new DocStoreConfig(),
                new ElasticConfig(),
                new ExplorerConfig(),
                new ExportConfig(),
                new FeedConfig(),
                new GitRepoConfig(),
                new IndexConfig(),
                new JobSystemConfig(),
                new KafkaConfig(),
                new LifecycleConfig(),
                new LmdbLibraryConfig(),
                new LoggingConfig(),
                new NodeConfig(),
                new NodeUriConfig(),
                new PipelineConfig(),
                new ProcessorConfig(),
                new PropertyServiceConfig(),
                new PublicUriConfig(),
                new IndexFieldDbConfig(),
                new ReceiveDataConfig(),
                new StroomReceiptPolicyConfig(),
                new S3Config(),
                new SearchConfig(),
                new SecurityConfig(),
                new SessionCookieConfig(),
                new SessionConfig(),
                new SolrConfig(),
                new StateConfig(),
                new PlanBConfig(),
                new StatisticsConfig(),
                new StoredQueryConfig(),
                new StroomPathConfig(),
                new UiConfig(),
                new UiUriConfig(),
                new VolumeConfig());
    }

    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public AppConfig(@JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE) final boolean haltBootOnConfigValidationFailure,
                     @JsonProperty(CrossModuleConfig.NAME) final CrossModuleConfig crossModuleConfig,
                     @JsonProperty(PROP_NAME_ACTIVITY) final ActivityConfig activityConfig,
                     @JsonProperty(PROP_NAME_ANALYTICS) final AnalyticsConfig analyticsConfig,
                     @JsonProperty(PROP_NAME_ANNOTATION) final AnnotationConfig annotationConfig,
                     @JsonProperty(PROP_NAME_CONTENT_STORE) final ContentStoreConfig contentStoreConfig,
                     @JsonProperty(PROP_NAME_AUTO_CONTENT_CREATION) final AutoContentCreationConfig autoContentCreationConfig,
                     @JsonProperty(PROP_NAME_BYTE_BUFFER_POOL) final ByteBufferPoolConfig byteBufferPoolConfig,
                     @JsonProperty(PROP_NAME_CHAT_MEMORY) final ChatMemoryConfig chatMemoryConfig,
                     @JsonProperty(PROP_NAME_CLUSTER) final ClusterConfig clusterConfig,
                     @JsonProperty(PROP_NAME_CLUSTER_LOCK) final ClusterLockConfig clusterLockConfig,
                     @JsonProperty(PROP_NAME_COMMON_DB_DETAILS) final CommonDbConfig commonDbConfig,
                     @JsonProperty(PROP_NAME_CONTENT_PACK_IMPORT) final ContentPackImportConfig contentPackImportConfig,
                     @JsonProperty(PROP_NAME_CREDENTIALS) final CredentialsConfig credentialsConfig,
                     @JsonProperty(PROP_NAME_DASHBOARD) final DashboardConfig dashboardConfig,
                     @JsonProperty(PROP_NAME_DATA) final DataConfig dataConfig,
                     @JsonProperty(PROP_NAME_DOCSTORE) final DocStoreConfig docStoreConfig,
                     @JsonProperty(PROP_NAME_ELASTIC) final ElasticConfig elasticConfig,
                     @JsonProperty(PROP_NAME_EXPLORER) final ExplorerConfig explorerConfig,
                     @JsonProperty(PROP_NAME_EXPORT) final ExportConfig exportConfig,
                     @JsonProperty(PROP_NAME_FEED) final FeedConfig feedConfig,
                     @JsonProperty(PROP_NAME_GIT_REPO) final GitRepoConfig gitRepoConfig,
                     @JsonProperty(PROP_NAME_INDEX) final IndexConfig indexConfig,
                     @JsonProperty(PROP_NAME_JOB) final JobSystemConfig jobSystemConfig,
                     @JsonProperty(PROP_NAME_KAFKA) final KafkaConfig kafkaConfig,
                     @JsonProperty(PROP_NAME_LIFECYCLE) final LifecycleConfig lifecycleConfig,
                     @JsonProperty(PROP_NAME_LMDB_LIBRARY) final LmdbLibraryConfig lmdbLibraryConfig,
                     @JsonProperty(PROP_NAME_LOGGING) final LoggingConfig loggingConfig,
                     @JsonProperty(PROP_NAME_NODE) final NodeConfig nodeConfig,
                     @JsonProperty(PROP_NAME_NODE_URI) final NodeUriConfig nodeUri,
                     @JsonProperty(PROP_NAME_PIPELINE) final PipelineConfig pipelineConfig,
                     @JsonProperty(PROP_NAME_PROCESSOR) final ProcessorConfig processorConfig,
                     @JsonProperty(PROP_NAME_PROPERTIES) final PropertyServiceConfig propertyServiceConfig,
                     @JsonProperty(PROP_NAME_PUBLIC_URI) final PublicUriConfig publicUri,
                     @JsonProperty(PROP_NAME_QUERY_DATASOURCE) final IndexFieldDbConfig queryDataSourceConfig,
                     @JsonProperty(PROP_NAME_RECEIVE) final ReceiveDataConfig receiveDataConfig,
                     @JsonProperty(PROP_NAME_RECEIPT_POLICY) final StroomReceiptPolicyConfig receiptPolicyConfig,
                     @JsonProperty(PROP_NAME_S3) final S3Config s3Config,
                     @JsonProperty(PROP_NAME_SEARCH) final SearchConfig searchConfig,
                     @JsonProperty(PROP_NAME_SECURITY) final SecurityConfig securityConfig,
                     @JsonProperty(PROP_NAME_SESSION_COOKIE) final SessionCookieConfig sessionCookieConfig,
                     @JsonProperty(PROP_NAME_SESSION) final SessionConfig sessionConfig,
                     @JsonProperty(PROP_NAME_SOLR) final SolrConfig solrConfig,
                     @JsonProperty(PROP_NAME_STATE) final StateConfig stateConfig,
                     @JsonProperty(PROP_NAME_PLANB) final PlanBConfig planBConfig,
                     @JsonProperty(PROP_NAME_STATISTICS) final StatisticsConfig statisticsConfig,
                     @JsonProperty(PROP_NAME_QUERY_HISTORY) final StoredQueryConfig storedQueryConfig,
                     @JsonProperty(PROP_NAME_PATH) final StroomPathConfig pathConfig,
                     @JsonProperty(PROP_NAME_UI) final UiConfig uiConfig,
                     @JsonProperty(PROP_NAME_UI_URI) final UiUriConfig uiUri,
                     @JsonProperty(PROP_NAME_VOLUMES) final VolumeConfig volumeConfig) {
        this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
        this.crossModuleConfig = crossModuleConfig;
        this.activityConfig = activityConfig;
        this.analyticsConfig = analyticsConfig;
        this.annotationConfig = annotationConfig;
        this.contentStoreConfig = contentStoreConfig;
        this.autoContentCreationConfig = autoContentCreationConfig;
        this.byteBufferPoolConfig = byteBufferPoolConfig;
        this.chatMemoryConfig = chatMemoryConfig;
        this.clusterConfig = clusterConfig;
        this.clusterLockConfig = clusterLockConfig;
        this.commonDbConfig = commonDbConfig;
        this.contentPackImportConfig = contentPackImportConfig;
        this.credentialsConfig = credentialsConfig;
        this.dashboardConfig = dashboardConfig;
        this.dataConfig = dataConfig;
        this.docStoreConfig = docStoreConfig;
        this.elasticConfig = elasticConfig;
        this.explorerConfig = explorerConfig;
        this.exportConfig = exportConfig;
        this.feedConfig = feedConfig;
        this.gitRepoConfig = gitRepoConfig;
        this.indexConfig = indexConfig;
        this.jobSystemConfig = jobSystemConfig;
        this.kafkaConfig = kafkaConfig;
        this.lifecycleConfig = lifecycleConfig;
        this.lmdbLibraryConfig = lmdbLibraryConfig;
        this.loggingConfig = loggingConfig;
        this.nodeConfig = nodeConfig;
        this.nodeUri = nodeUri;
        this.pipelineConfig = pipelineConfig;
        this.processorConfig = processorConfig;
        this.propertyServiceConfig = propertyServiceConfig;
        this.publicUri = publicUri;
        this.queryDataSourceConfig = queryDataSourceConfig;
        this.receiveDataConfig = receiveDataConfig;
        this.receiptPolicyConfig = receiptPolicyConfig;
        this.s3Config = s3Config;
        this.searchConfig = searchConfig;
        this.securityConfig = securityConfig;
        this.sessionCookieConfig = sessionCookieConfig;
        this.sessionConfig = sessionConfig;
        this.solrConfig = solrConfig;
        this.stateConfig = stateConfig;
        this.planBConfig = planBConfig;
        this.statisticsConfig = statisticsConfig;
        this.storedQueryConfig = storedQueryConfig;
        this.pathConfig = pathConfig;
        this.uiConfig = uiConfig;
        this.uiUri = uiUri;
        this.volumeConfig = volumeConfig;
    }

    @AssertTrue(
            message = "stroom." + PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE + " is set to false. If there is " +
                      "invalid configuration the system may behave in unexpected ways. This setting is not advised.",
            payload = ValidationSeverity.Warning.class)
    @JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE)
    @JsonPropertyDescription("If true, Stroom will halt on start up if any errors are found in the YAML " +
                             "configuration file. If false, the errors will simply be logged." +
                             "Setting this to false is not advised.")
    public boolean isHaltBootOnConfigValidationFailure() {
        return haltBootOnConfigValidationFailure;
    }

    @JsonProperty(CrossModuleConfig.NAME)
    public CrossModuleConfig getAppDbConfig() {
        return crossModuleConfig;
    }

    @JsonProperty(PROP_NAME_ACTIVITY)
    public ActivityConfig getActivityConfig() {
        return activityConfig;
    }

    @JsonProperty(PROP_NAME_ANALYTICS)
    public AnalyticsConfig getAnalyticsConfig() {
        return analyticsConfig;
    }

    @JsonProperty(PROP_NAME_ANNOTATION)
    public AnnotationConfig getAnnotationConfig() {
        return annotationConfig;
    }

    @JsonProperty(PROP_NAME_CONTENT_STORE)
    public ContentStoreConfig getContentStoreConfig() {
        return contentStoreConfig;
    }

    @JsonProperty(PROP_NAME_CREDENTIALS)
    public CredentialsConfig getCredentialsConfig() {
        return credentialsConfig;
    }

    @JsonProperty(PROP_NAME_AUTO_CONTENT_CREATION)
    public AutoContentCreationConfig getAutoContentCreationConfig() {
        return autoContentCreationConfig;
    }

    @JsonProperty(PROP_NAME_BYTE_BUFFER_POOL)
    public ByteBufferPoolConfig getByteBufferPoolConfig() {
        return byteBufferPoolConfig;
    }

    @JsonProperty(PROP_NAME_CHAT_MEMORY)
    public ChatMemoryConfig getChatMemoryConfig() {
        return chatMemoryConfig;
    }

    @JsonProperty(PROP_NAME_CLUSTER)
    public ClusterConfig getClusterConfig() {
        return clusterConfig;
    }

    @JsonProperty(PROP_NAME_CLUSTER_LOCK)
    public ClusterLockConfig getClusterLockConfig() {
        return clusterLockConfig;
    }

    @JsonProperty(PROP_NAME_COMMON_DB_DETAILS)
    @JsonPropertyDescription("Defines a set of common database connection details to use if no connection details " +
                             "are defined for a service area in stroom, e.g. core or config. This means you can " +
                             "have all service areas running in a single database, have each in their own " +
                             "database or a mixture.")
    public CommonDbConfig getCommonDbConfig() {
        return commonDbConfig;
    }

    @JsonProperty(PROP_NAME_CONTENT_PACK_IMPORT)
    public ContentPackImportConfig getContentPackImportConfig() {
        return contentPackImportConfig;
    }

    @JsonProperty(PROP_NAME_DASHBOARD)
    @JsonPropertyDescription("Configuration for the dashboards")
    public DashboardConfig getDashboardConfig() {
        return dashboardConfig;
    }

    @JsonProperty(PROP_NAME_DATA)
    @JsonPropertyDescription("Configuration for the data layer of stroom")
    public DataConfig getDataConfig() {
        return dataConfig;
    }

    @JsonProperty(PROP_NAME_DOCSTORE)
    public DocStoreConfig getDocStoreConfig() {
        return docStoreConfig;
    }

    @JsonProperty(PROP_NAME_ELASTIC)
    public ElasticConfig getElasticConfig() {
        return elasticConfig;
    }

    @JsonProperty(PROP_NAME_EXPLORER)
    public ExplorerConfig getExplorerConfig() {
        return explorerConfig;
    }

    @JsonProperty(PROP_NAME_FEED)
    public FeedConfig getFeedConfig() {
        return feedConfig;
    }

    @JsonProperty(PROP_NAME_EXPORT)
    public ExportConfig getExportConfig() {
        return exportConfig;
    }

    @JsonProperty(PROP_NAME_GIT_REPO)
    public GitRepoConfig getGitRepoConfig() {
        return gitRepoConfig;
    }

    @JsonProperty(PROP_NAME_INDEX)
    public IndexConfig getIndexConfig() {
        return indexConfig;
    }

    @JsonProperty(PROP_NAME_JOB)
    public JobSystemConfig getJobSystemConfig() {
        return jobSystemConfig;
    }

    @JsonProperty(PROP_NAME_KAFKA)
    public KafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }

    @JsonProperty(PROP_NAME_LIFECYCLE)
    public LifecycleConfig getLifecycleConfig() {
        return lifecycleConfig;
    }

    @JsonProperty(PROP_NAME_LMDB_LIBRARY)
    public LmdbLibraryConfig getLmdbLibraryConfig() {
        return lmdbLibraryConfig;
    }

    @JsonProperty(PROP_NAME_NODE)
    public NodeConfig getNodeConfig() {
        return nodeConfig;
    }

    @JsonPropertyDescription("This is the base endpoint of the node for all inter-node communications, " +
                             "i.e. all cluster management and node info calls. " +
                             "This endpoint will typically be hidden behind a firewall and not be " +
                             "publicly available. The address must be resolvable from all other nodes " +
                             "in the cluster. This does not need to be set for a single node cluster.")
    @JsonProperty(PROP_NAME_NODE_URI)
    public NodeUriConfig getNodeUri() {
        return nodeUri;
    }

    @JsonProperty(PROP_NAME_PATH)
    public StroomPathConfig getPathConfig() {
        return pathConfig;
    }

    @JsonProperty(PROP_NAME_PIPELINE)
    public PipelineConfig getPipelineConfig() {
        return pipelineConfig;
    }

    @JsonProperty(PROP_NAME_PROCESSOR)
    public ProcessorConfig getProcessorConfig() {
        return processorConfig;
    }

    @JsonProperty(PROP_NAME_PROPERTIES)
    @JsonPropertyDescription("Configuration for the stroom property service")
    public PropertyServiceConfig getPropertyServiceConfig() {
        return propertyServiceConfig;
    }

    @JsonPropertyDescription("This is public facing URI of stroom which may be different from the local host if " +
                             "behind a proxy")
    @JsonProperty(PROP_NAME_PUBLIC_URI)
    public PublicUriConfig getPublicUri() {
        return publicUri;
    }

    @JsonProperty(PROP_NAME_QUERY_DATASOURCE)
    @JsonPropertyDescription("Configuration for the stroom query datasource service")
    public IndexFieldDbConfig getQueryDataSourceConfig() {
        return queryDataSourceConfig;
    }

    @JsonProperty(PROP_NAME_QUERY_HISTORY)
    public StoredQueryConfig getStoredQueryConfig() {
        return storedQueryConfig;
    }

    @JsonProperty(PROP_NAME_RECEIVE)
    public ReceiveDataConfig getReceiveDataConfig() {
        return receiveDataConfig;
    }

    @JsonProperty(PROP_NAME_RECEIPT_POLICY)
    public StroomReceiptPolicyConfig getReceiptPolicyConfig() {
        return receiptPolicyConfig;
    }

    @JsonProperty(PROP_NAME_LOGGING)
    public LoggingConfig getRequestLoggingConfig() {
        return loggingConfig;
    }

    @JsonProperty(PROP_NAME_S3)
    @JsonPropertyDescription("Default configuration settings for S3")
    public S3Config getS3Config() {
        return s3Config;
    }

    @JsonProperty(PROP_NAME_SEARCH)
    public SearchConfig getSearchConfig() {
        return searchConfig;
    }

    @JsonProperty(PROP_NAME_SOLR)
    public SolrConfig getSolrConfig() {
        return solrConfig;
    }

    @JsonProperty(PROP_NAME_SECURITY)
    public SecurityConfig getSecurityConfig() {
        return securityConfig;
    }

    @JsonProperty(PROP_NAME_SESSION_COOKIE)
    public SessionCookieConfig getSessionCookieConfig() {
        return sessionCookieConfig;
    }

    @JsonProperty(PROP_NAME_SESSION)
    public SessionConfig getSessionConfig() {
        return sessionConfig;
    }

    @JsonProperty(PROP_NAME_STATE)
    @JsonPropertyDescription("Configuration for the stroom state service")
    public StateConfig getStateConfig() {
        return stateConfig;
    }

    @JsonProperty(PROP_NAME_PLANB)
    @JsonPropertyDescription("Configuration for the stroom Plan B state service")
    public PlanBConfig getPlanBConfig() {
        return planBConfig;
    }

    @JsonProperty(PROP_NAME_STATISTICS)
    @JsonPropertyDescription("Configuration for the stroom statistics service")
    public StatisticsConfig getStatisticsConfig() {
        return statisticsConfig;
    }

    @JsonProperty(PROP_NAME_UI)
    public UiConfig getUiConfig() {
        return uiConfig;
    }

    @JsonPropertyDescription("This is the URI where the UI is hosted if different to the public facing URI of the " +
                             "server, e.g. during development or some other deployments")
    @JsonProperty(PROP_NAME_UI_URI)
    public UiUriConfig getUiUri() {
        return uiUri;
    }

    @JsonProperty(PROP_NAME_VOLUMES)
    public VolumeConfig getVolumeConfig() {
        return volumeConfig;
    }
}
