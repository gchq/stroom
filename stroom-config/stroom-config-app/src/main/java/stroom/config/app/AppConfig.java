package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.activity.impl.db.ActivityConfig;
import stroom.annotation.impl.AnnotationConfig;
import stroom.cluster.api.ClusterConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.cluster.task.impl.ClusterTaskConfig;
import stroom.config.common.CommonDbConfig;
import stroom.core.benchmark.BenchmarkClusterConfig;
import stroom.core.db.CoreConfig;
import stroom.core.receive.ProxyAggregationConfig;
import stroom.core.receive.ReceiveDataConfig;
import stroom.dashboard.impl.DashboardConfig;
import stroom.dashboard.impl.datasource.DataSourceUrlConfig;
import stroom.docstore.impl.db.DocStoreConfig;
import stroom.explorer.impl.db.ExplorerConfig;
import stroom.feed.impl.FeedConfig;
import stroom.importexport.impl.ContentPackImportConfig;
import stroom.importexport.impl.ExportConfig;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.selection.VolumeConfig;
import stroom.job.impl.JobSystemConfig;
import stroom.lifecycle.impl.LifecycleConfig;
import stroom.node.impl.NodeConfig;
import stroom.pipeline.PipelineConfig;
import stroom.processor.impl.ProcessorConfig;
import stroom.search.impl.SearchConfig;
import stroom.search.solr.SolrConfig;
import stroom.searchable.impl.SearchableConfig;
import stroom.security.impl.SecurityConfig;
import stroom.servicediscovery.impl.ServiceDiscoveryConfig;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.util.io.PathConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class AppConfig implements IsConfig {
    private boolean superDevMode;
    private ActivityConfig activityConfig = new ActivityConfig();
    private AnnotationConfig annotationConfig = new AnnotationConfig();
    private BenchmarkClusterConfig benchmarkClusterConfig = new BenchmarkClusterConfig();
    private ClusterConfig clusterConfig = new ClusterConfig();
    private ClusterLockConfig clusterLockConfig = new ClusterLockConfig();
    private ClusterTaskConfig clusterTaskConfig = new ClusterTaskConfig();
    private CommonDbConfig commonDbConfig = new CommonDbConfig();
    private ContentPackImportConfig contentPackImportConfig = new ContentPackImportConfig();
    private CoreConfig coreConfig = new CoreConfig();
    private DashboardConfig dashboardConfig = new DashboardConfig();
    private DataConfig dataConfig = new DataConfig();
    private DataSourceUrlConfig dataSourceUrlConfig = new DataSourceUrlConfig();
    private DocStoreConfig docStoreConfig = new DocStoreConfig();
    private ExplorerConfig explorerConfig = new ExplorerConfig();
    private ExportConfig exportConfig = new ExportConfig();
    private FeedConfig feedConfig = new FeedConfig();
    private IndexConfig indexConfig = new IndexConfig();
    private JobSystemConfig jobSystemConfig = new JobSystemConfig();
    private LifecycleConfig lifecycleConfig = new LifecycleConfig();
    private NodeConfig nodeConfig = new NodeConfig();
    private PathConfig pathConfig = new PathConfig();
    private PipelineConfig pipelineConfig = new PipelineConfig();
    private ProcessorConfig processorConfig = new ProcessorConfig();
    private PropertyServiceConfig propertyServiceConfig = new PropertyServiceConfig();
    private ProxyAggregationConfig proxyAggregationConfig = new ProxyAggregationConfig();
    private ReceiveDataConfig receiveDataConfig = new ReceiveDataConfig();
    private SearchConfig searchConfig = new SearchConfig();
    private SearchableConfig searchableConfig = new SearchableConfig();
    private SecurityConfig securityConfig = new SecurityConfig();
    private ServiceDiscoveryConfig serviceDiscoveryConfig = new ServiceDiscoveryConfig();
    private SessionCookieConfig sessionCookieConfig = new SessionCookieConfig();
    private SolrConfig solrConfig = new SolrConfig();
    private StatisticsConfig statisticsConfig = new StatisticsConfig();
    private StoredQueryConfig storedQueryConfig = new StoredQueryConfig();
    private UiConfig uiConfig = new UiConfig();
    private VolumeConfig volumeConfig = new VolumeConfig();

    @JsonProperty("superDevMode")
    public boolean isSuperDevMode() {
        return superDevMode;
    }

    @JsonProperty("superDevMode")
    public void setSuperDevMode(final boolean superDevMode) {
        this.superDevMode = superDevMode;
    }

    @JsonProperty("activity")
    public ActivityConfig getActivityConfig() {
        return activityConfig;
    }

    public void setActivityConfig(final ActivityConfig activityConfig) {
        this.activityConfig = activityConfig;
    }

    @JsonProperty("annotation")
    public AnnotationConfig getAnnotationConfig() {
        return annotationConfig;
    }

    public void setAnnotationConfig(final AnnotationConfig annotationConfig) {
        this.annotationConfig = annotationConfig;
    }

    @JsonProperty("benchmark")
    public BenchmarkClusterConfig getBenchmarkClusterConfig() {
        return benchmarkClusterConfig;
    }

    public void setBenchmarkClusterConfig(final BenchmarkClusterConfig benchmarkClusterConfig) {
        this.benchmarkClusterConfig = benchmarkClusterConfig;
    }

    @JsonProperty("cluster")
    public ClusterConfig getClusterConfig() {
        return clusterConfig;
    }

    public void setClusterConfig(final ClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
    }

    @JsonProperty("clusterLock")
    public ClusterLockConfig getClusterLockConfig() {
        return clusterLockConfig;
    }

    public void setClusterLockConfig(ClusterLockConfig clusterLockConfig) {
        this.clusterLockConfig = clusterLockConfig;
    }

    @JsonProperty("clusterTask")
    public ClusterTaskConfig getClusterTaskConfig() {
        return clusterTaskConfig;
    }

    public void setClusterTaskConfig(final ClusterTaskConfig clusterTaskConfig) {
        this.clusterTaskConfig = clusterTaskConfig;
    }

    @JsonProperty("commonDbDetails")
    @JsonPropertyDescription("Defines a set of common database connection details to use if no connection details are " +
            "defined for a service area in stroom, e.g. core or config")
    public CommonDbConfig getCommonDbConfig() {
        return commonDbConfig;
    }

    public void setCommonDbConfig(final CommonDbConfig commonDbConfig) {
        this.commonDbConfig = commonDbConfig;
    }

    @JsonProperty("contentPackImport")
    public ContentPackImportConfig getContentPackImportConfig() {
        return contentPackImportConfig;
    }

    public void setContentPackImportConfig(final ContentPackImportConfig contentPackImportConfig) {
        this.contentPackImportConfig = contentPackImportConfig;
    }

    @JsonProperty("core")
    @JsonPropertyDescription("Configuration for the core stroom DB")
    public CoreConfig getCoreConfig() {
        return coreConfig;
    }

    public void setCoreConfig(final CoreConfig coreConfig) {
        this.coreConfig = coreConfig;
    }

    @JsonProperty("dashboard")
    public DashboardConfig getDashboardConfig() {
        return dashboardConfig;
    }

    public void setDashboardConfig(final DashboardConfig dashboardConfig) {
        this.dashboardConfig = dashboardConfig;
    }

    @JsonProperty("data")
    @JsonPropertyDescription("Configuration for the data layer of stroom")
    public DataConfig getDataConfig() {
        return dataConfig;
    }

    public void setDataConfig(final DataConfig dataConfig) {
        this.dataConfig = dataConfig;
    }

    @JsonProperty("dataSourceUrl")
    public DataSourceUrlConfig getDataSourceUrlConfig() {
        return dataSourceUrlConfig;
    }

    public void setDataSourceUrlConfig(final DataSourceUrlConfig dataSourceUrlConfig) {
        this.dataSourceUrlConfig = dataSourceUrlConfig;
    }

    @JsonProperty("docstore")
    public DocStoreConfig getDocStoreConfig() {
        return docStoreConfig;
    }

    public void setDocStoreConfig(final DocStoreConfig docStoreConfig) {
        this.docStoreConfig = docStoreConfig;
    }

    @JsonProperty("explorer")
    public ExplorerConfig getExplorerConfig() {
        return explorerConfig;
    }

    public void setExplorerConfig(final ExplorerConfig explorerConfig) {
        this.explorerConfig = explorerConfig;
    }

    @JsonProperty("feed")
    public FeedConfig getFeedConfig() {
        return feedConfig;
    }

    public void setFeedConfig(final FeedConfig feedConfig) {
        this.feedConfig = feedConfig;
    }

    @JsonProperty("export")
    public ExportConfig getExportConfig() {
        return exportConfig;
    }

    public void setExportConfig(final ExportConfig exportConfig) {
        this.exportConfig = exportConfig;
    }

    @JsonProperty("index")
    public IndexConfig getIndexConfig() {
        return indexConfig;
    }

    public void setIndexConfig(final IndexConfig indexConfig) {
        this.indexConfig = indexConfig;
    }

    @JsonProperty("job")
    public JobSystemConfig getJobSystemConfig() {
        return jobSystemConfig;
    }

    public void setJobSystemConfig(final JobSystemConfig jobSystemConfig) {
        this.jobSystemConfig = jobSystemConfig;
    }

    @JsonProperty("lifecycle")
    public LifecycleConfig getLifecycleConfig() {
        return lifecycleConfig;
    }

    public void setLifecycleConfig(final LifecycleConfig lifecycleConfig) {
        this.lifecycleConfig = lifecycleConfig;
    }

    @JsonProperty("node")
    public NodeConfig getNodeConfig() {
        return nodeConfig;
    }

    public void setNodeConfig(final NodeConfig nodeConfig) {
        this.nodeConfig = nodeConfig;
    }

    @JsonProperty("path")
    public PathConfig getPathConfig() {
        return pathConfig;
    }

    public void setPathConfig(final PathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @JsonProperty("pipeline")
    public PipelineConfig getPipelineConfig() {
        return pipelineConfig;
    }

    public void setPipelineConfig(final PipelineConfig pipelineConfig) {
        this.pipelineConfig = pipelineConfig;
    }

    @JsonProperty("processor")
    public ProcessorConfig getProcessorConfig() {
        return processorConfig;
    }

    public void setProcessorConfig(final ProcessorConfig processorConfig) {
        this.processorConfig = processorConfig;
    }

    @JsonProperty("properties")
    @JsonPropertyDescription("Configuration for the stroom property service")
    public PropertyServiceConfig getPropertyServiceConfig() {
        return propertyServiceConfig;
    }

    public void setPropertyServiceConfig(final PropertyServiceConfig propertyServiceConfig) {
        this.propertyServiceConfig = propertyServiceConfig;
    }

    @JsonProperty("proxyAggregation")
    public ProxyAggregationConfig getProxyAggregationConfig() {
        return proxyAggregationConfig;
    }

    public void setProxyAggregationConfig(final ProxyAggregationConfig proxyAggregationConfig) {
        this.proxyAggregationConfig = proxyAggregationConfig;
    }

    @JsonProperty("queryHistory")
    public StoredQueryConfig getStoredQueryConfig() {
        return storedQueryConfig;
    }

    public void setStoredQueryConfig(final StoredQueryConfig storedQueryConfig) {
        this.storedQueryConfig = storedQueryConfig;
    }

    @JsonProperty("receive")
    public ReceiveDataConfig getReceiveDataConfig() {
        return receiveDataConfig;
    }

    public void setReceiveDataConfig(final ReceiveDataConfig receiveDataConfig) {
        this.receiveDataConfig = receiveDataConfig;
    }

    @JsonProperty("search")
    public SearchConfig getSearchConfig() {
        return searchConfig;
    }

    public void setSearchConfig(final SearchConfig searchConfig) {
        this.searchConfig = searchConfig;
    }

    @JsonProperty("searchable")
    public SearchableConfig getSearchableConfig() {
        return searchableConfig;
    }

    public void setSearchableConfig(final SearchableConfig searchableConfig) {
        this.searchableConfig = searchableConfig;
    }

    @JsonProperty("solr")
    public SolrConfig getSolrConfig() {
        return solrConfig;
    }

    public void setSolrConfig(final SolrConfig solrConfig) {
        this.solrConfig = solrConfig;
    }

    @JsonProperty("security")
    public SecurityConfig getSecurityConfig() {
        return securityConfig;
    }

    public void setSecurityConfig(final SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @JsonProperty("serviceDiscovery")
    public ServiceDiscoveryConfig getServiceDiscoveryConfig() {
        return serviceDiscoveryConfig;
    }

    public void setServiceDiscoveryConfig(final ServiceDiscoveryConfig serviceDiscoveryConfig) {
        this.serviceDiscoveryConfig = serviceDiscoveryConfig;
    }

    @JsonProperty("sessionCookie")
    public SessionCookieConfig getSessionCookieConfig() {
        return sessionCookieConfig;
    }

    @JsonProperty("sessionCookie")
    public void setSessionCookieConfig(final SessionCookieConfig sessionCookieConfig) {
        this.sessionCookieConfig = sessionCookieConfig;
    }

    @JsonProperty("statistics")
    @JsonPropertyDescription("Configuration for the stroom statistics service")
    public StatisticsConfig getStatisticsConfig() {
        return statisticsConfig;
    }

    public void setStatisticsConfig(final StatisticsConfig statisticsConfig) {
        this.statisticsConfig = statisticsConfig;
    }

    @JsonProperty("ui")
    public UiConfig getUiConfig() {
        return uiConfig;
    }

    public void setUiConfig(final UiConfig uiConfig) {
        this.uiConfig = uiConfig;
    }

    @JsonProperty("volumes")
    public VolumeConfig getVolumeConfig() {
        return volumeConfig;
    }

    public void setVolumeConfig(final VolumeConfig volumeConfig) {
        this.volumeConfig = volumeConfig;
    }
}
