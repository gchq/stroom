package stroom.config.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.activity.impl.db.ActivityConfig;
import stroom.cluster.api.ClusterConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.config.common.CommonDbConfig;
import stroom.core.benchmark.BenchmarkClusterConfig;
import stroom.core.db.CoreConfig;
import stroom.core.receive.ProxyAggregationConfig;
import stroom.core.receive.ReceiveDataConfig;
import stroom.dashboard.impl.datasource.DataSourceUrlConfig;
import stroom.explorer.impl.db.ExplorerConfig;
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
import stroom.security.impl.SecurityConfig;
import stroom.servicediscovery.impl.ServiceDiscoveryConfig;
import stroom.storedquery.impl.StoredQueryHistoryConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.util.io.PathConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppConfig implements IsConfig {
    private ActivityConfig activityConfig;
    private BenchmarkClusterConfig benchmarkClusterConfig;
    private ClusterConfig clusterConfig;
    private ClusterLockConfig clusterLockConfig;
    private ContentPackImportConfig contentPackImportConfig;
    private CoreConfig coreConfig;
    private DataConfig dataConfig;
    private DataSourceUrlConfig dataSourceUrlConfig;
    private CommonDbConfig commonDbConfig;
    private ExplorerConfig explorerConfig;
    private ExportConfig exportConfig;
    private IndexConfig indexConfig;
    private JobSystemConfig jobSystemConfig;
    private LifecycleConfig lifecycleConfig;
    private NodeConfig nodeConfig;
    private PathConfig pathConfig;
    private PipelineConfig pipelineConfig;
    private ProcessorConfig processorConfig;
    private PropertyServiceConfig propertyServiceConfig;
    private ProxyAggregationConfig proxyAggregationConfig;
    private ReceiveDataConfig receiveDataConfig;
    private SearchConfig searchConfig;
    private SecurityConfig securityConfig;
    private ServiceDiscoveryConfig serviceDiscoveryConfig;
    private StatisticsConfig statisticsConfig;
    private StoredQueryHistoryConfig storedQueryHistoryConfig;
    private UiConfig uiConfig;
    private VolumeConfig volumeConfig;

    public AppConfig() {
        this.activityConfig = new ActivityConfig();
        this.benchmarkClusterConfig = new BenchmarkClusterConfig();
        this.clusterConfig = new ClusterConfig();
        this.clusterLockConfig = new ClusterLockConfig();
        this.contentPackImportConfig = new ContentPackImportConfig();
        this.coreConfig = new CoreConfig();
        this.dataConfig = new DataConfig();
        this.dataSourceUrlConfig = new DataSourceUrlConfig();
        this.commonDbConfig = new CommonDbConfig();
        this.explorerConfig = new ExplorerConfig();
        this.exportConfig = new ExportConfig();
        this.indexConfig = new IndexConfig();
        this.jobSystemConfig = new JobSystemConfig();
        this.lifecycleConfig = new LifecycleConfig();
        this.nodeConfig = new NodeConfig();
        this.pathConfig = new PathConfig();
        this.pipelineConfig = new PipelineConfig();
        this.processorConfig = new ProcessorConfig();
        this.propertyServiceConfig = new PropertyServiceConfig();
        this.proxyAggregationConfig = new ProxyAggregationConfig();
        this.receiveDataConfig = new ReceiveDataConfig();
        this.searchConfig = new SearchConfig();
        this.securityConfig = new SecurityConfig();
        this.serviceDiscoveryConfig = new ServiceDiscoveryConfig();
        this.statisticsConfig = new StatisticsConfig();
        this.storedQueryHistoryConfig = new StoredQueryHistoryConfig();
        this.uiConfig = new UiConfig();
        this.volumeConfig = new VolumeConfig();
    }

    @Inject
    AppConfig(final BenchmarkClusterConfig benchmarkClusterConfig,
              final ClusterConfig clusterConfig,
              final ClusterLockConfig clusterLockConfig,
              final ContentPackImportConfig contentPackImportConfig,
              final CoreConfig coreConfig,
              final DataConfig dataConfig,
              final DataSourceUrlConfig dataSourceUrlConfig,
              final CommonDbConfig commonDbConfig,
              final ExplorerConfig explorerConfig,
              final ExportConfig exportConfig,
              final IndexConfig indexConfig,
              final JobSystemConfig jobSystemConfig,
              final LifecycleConfig lifecycleConfig,
              final NodeConfig nodeConfig,
              final PathConfig pathConfig,
              final PipelineConfig pipelineConfig,
              final ProcessorConfig processorConfig,
              final PropertyServiceConfig propertyServiceConfig,
              final ProxyAggregationConfig proxyAggregationConfig,
              final ReceiveDataConfig receiveDataConfig,
              final SearchConfig searchConfig,
              final SecurityConfig securityConfig,
              final ServiceDiscoveryConfig serviceDiscoveryConfig,
              final StatisticsConfig statisticsConfig,
              final StoredQueryHistoryConfig storedQueryHistoryConfig,
              final UiConfig uiConfig,
              final VolumeConfig volumeConfig,
              final ActivityConfig activityConfig ) {
        this.activityConfig = activityConfig;
        this.benchmarkClusterConfig = benchmarkClusterConfig;
        this.clusterConfig = clusterConfig;
        this.clusterLockConfig = clusterLockConfig;
        this.contentPackImportConfig = contentPackImportConfig;
        this.coreConfig = coreConfig;
        this.dataConfig = dataConfig;
        this.dataSourceUrlConfig = dataSourceUrlConfig;
        this.commonDbConfig = commonDbConfig;
        this.explorerConfig = explorerConfig;
        this.exportConfig = exportConfig;
        this.indexConfig = indexConfig;
        this.jobSystemConfig = jobSystemConfig;
        this.lifecycleConfig = lifecycleConfig;
        this.nodeConfig = nodeConfig;
        this.pathConfig = pathConfig;
        this.pipelineConfig = pipelineConfig;
        this.processorConfig = processorConfig;
        this.propertyServiceConfig = propertyServiceConfig;
        this.proxyAggregationConfig = proxyAggregationConfig;
        this.receiveDataConfig = receiveDataConfig;
        this.searchConfig = searchConfig;
        this.securityConfig = securityConfig;
        this.serviceDiscoveryConfig = serviceDiscoveryConfig;
        this.statisticsConfig = statisticsConfig;
        this.storedQueryHistoryConfig = storedQueryHistoryConfig;
        this.uiConfig = uiConfig;
        this.volumeConfig = volumeConfig;
    }

    @JsonProperty("activity")
    public ActivityConfig getActivityConfig() {
        return activityConfig;
    }

    public void setActivityConfig(final ActivityConfig activityConfig) {
        this.activityConfig = activityConfig;
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

    @JsonProperty("commonDbDetails")
    public CommonDbConfig getCommonDbConfig() {
        return commonDbConfig;
    }

    void setCommonDbConfig(final CommonDbConfig commonDbConfig) {
        this.commonDbConfig = commonDbConfig;
    }

    @JsonProperty("explorer")
    public ExplorerConfig getExplorerConfig() {
        return explorerConfig;
    }

    public void setExplorerConfig(final ExplorerConfig explorerConfig) {
        this.explorerConfig = explorerConfig;
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
    public StoredQueryHistoryConfig getStoredQueryHistoryConfig() {
        return storedQueryHistoryConfig;
    }

    public void setStoredQueryHistoryConfig(final StoredQueryHistoryConfig storedQueryHistoryConfig) {
        this.storedQueryHistoryConfig = storedQueryHistoryConfig;
    }

    @JsonProperty("feed")
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
