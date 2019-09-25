package stroom.config.app;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.api.ClusterConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.config.common.CommonDbConfig;
import stroom.core.benchmark.BenchmarkClusterConfig;
import stroom.core.db.CoreConfig;
import stroom.core.receive.ProxyAggregationConfig;
import stroom.core.receive.ReceiveDataConfig;
import stroom.dashboard.impl.datasource.DataSourceUrlConfig;
import stroom.data.retention.impl.DataRetentionConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.explorer.impl.db.ExplorerConfig;
import stroom.importexport.impl.ContentPackImportConfig;
import stroom.importexport.impl.ExportConfig;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.selection.VolumeConfig;
import stroom.job.impl.JobSystemConfig;
import stroom.lifecycle.impl.LifecycleConfig;
import stroom.meta.impl.db.MetaServiceConfig;
import stroom.node.impl.HeapHistogramConfig;
import stroom.node.impl.NodeConfig;
import stroom.node.impl.StatusConfig;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.refdata.store.RefDataStoreConfig;
import stroom.processor.impl.ProcessorConfig;
import stroom.search.extraction.ExtractionConfig;
import stroom.search.impl.SearchConfig;
import stroom.search.impl.shard.IndexShardSearchConfig;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.SecurityConfig;
import stroom.servicediscovery.impl.ServiceDiscoveryConfig;
import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.storedquery.impl.StoredQueryHistoryConfig;
import stroom.ui.config.shared.ActivityConfig;
import stroom.ui.config.shared.QueryConfig;
import stroom.ui.config.shared.SplashConfig;
import stroom.ui.config.shared.ThemeConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;
import stroom.util.io.PathConfig;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Path;

public class AppConfigModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigModule.class);

    private final AppConfig appConfig;
    private final Path configFile;

    /**
     * Intended for test use
     */
    AppConfigModule( final Path configFile) {
        try {
            this.appConfig = YamlUtil.readAppConfig(configFile);
            this.configFile = configFile;
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error loading config file {}", configFile.toAbsolutePath()), e);
        }
    }

    public AppConfigModule(final AppConfig appConfig, final Path configFile) {
        this.appConfig = appConfig;
        this.configFile = configFile;
    }

    @Override
    protected void configure() {

        // Bind the application config.        
        bind(AppConfig.class).toInstance(appConfig);

        bind(AppConfigMonitor.class).toInstance(new AppConfigMonitor(appConfig, configFile));

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own
        bind(ActivityConfig.class).toInstance(appConfig.getUiConfig().getActivityConfig());
        bind(AppenderConfig.class).toInstance(appConfig.getPipelineConfig().getAppenderConfig());
        bind(AuthenticationConfig.class).toInstance(appConfig.getSecurityConfig().getAuthenticationConfig());
        bind(BenchmarkClusterConfig.class).toInstance(appConfig.getBenchmarkClusterConfig());
        bind(ClusterConfig.class).toInstance(appConfig.getClusterConfig());
        bind(ClusterLockConfig.class).toInstance(appConfig.getClusterLockConfig());
        bind(CommonDbConfig.class).toInstance(appConfig.getCommonDbConfig());
        bind(ContentPackImportConfig.class).toInstance(appConfig.getContentPackImportConfig());
        bind(CoreConfig.class).toInstance(appConfig.getCoreConfig());
        bind(DataConfig.class).toInstance(appConfig.getDataConfig());
        bind(DataRetentionConfig.class).toInstance(appConfig.getDataConfig().getDataRetentionConfig());
        bind(DataSourceUrlConfig.class).toInstance(appConfig.getDataSourceUrlConfig());
        bind(DataStoreServiceConfig.class).toInstance(appConfig.getDataConfig().getDataStoreServiceConfig());
        bind(ExplorerConfig.class).toInstance(appConfig.getExplorerConfig());
        bind(ExportConfig.class).toInstance(appConfig.getExportConfig());
        bind(ExtractionConfig.class).toInstance(appConfig.getSearchConfig().getExtractionConfig());
        bind(HBaseStatisticsConfig.class).toInstance(appConfig.getStatisticsConfig().getHbaseStatisticsConfig());
        bind(HeapHistogramConfig.class).toInstance(appConfig.getNodeConfig().getStatusConfig().getHeapHistogramConfig());
        bind(IndexConfig.class).toInstance(appConfig.getIndexConfig());
        bind(IndexShardSearchConfig.class).toInstance(appConfig.getSearchConfig().getShardConfig());
        bind(InternalStatisticsConfig.class).toInstance(appConfig.getStatisticsConfig().getInternalStatisticsConfig());
        bind(JobSystemConfig.class).toInstance(appConfig.getJobSystemConfig());
        bind(LifecycleConfig.class).toInstance(appConfig.getLifecycleConfig());
        bind(MetaServiceConfig.class).toInstance(appConfig.getDataConfig().getMetaServiceConfig());
        bind(NodeConfig.class).toInstance(appConfig.getNodeConfig());
        bind(PathConfig.class).toInstance(appConfig.getPathConfig());
        bind(PipelineConfig.class).toInstance(appConfig.getPipelineConfig());
        bind(ProcessorConfig.class).toInstance(appConfig.getProcessorConfig());
        bind(PropertyServiceConfig.class).toInstance(appConfig.getPropertyServiceConfig());
        bind(ProxyAggregationConfig.class).toInstance(appConfig.getProxyAggregationConfig());
        bind(QueryConfig.class).toInstance(appConfig.getUiConfig().getQueryConfig());
        bind(ReceiveDataConfig.class).toInstance(appConfig.getReceiveDataConfig());
        bind(RefDataStoreConfig.class).toInstance(appConfig.getPipelineConfig().getRefDataStoreConfig());
        bind(SQLStatisticsConfig.class).toInstance(appConfig.getStatisticsConfig().getSqlStatisticsConfig());
        bind(SearchConfig.class).toInstance(appConfig.getSearchConfig());
        bind(SecurityConfig.class).toInstance(appConfig.getSecurityConfig());
        bind(ServiceDiscoveryConfig.class).toInstance(appConfig.getServiceDiscoveryConfig());
        bind(SplashConfig.class).toInstance(appConfig.getUiConfig().getSplashConfig());
        bind(StatisticsConfig.class).toInstance(appConfig.getStatisticsConfig());
        bind(StatusConfig.class).toInstance(appConfig.getNodeConfig().getStatusConfig());
        bind(StoredQueryHistoryConfig.class).toInstance(appConfig.getStoredQueryHistoryConfig());
        bind(ThemeConfig.class).toInstance(appConfig.getUiConfig().getThemeConfig());
        bind(UiConfig.class).toInstance(appConfig.getUiConfig());
        bind(UrlConfig.class).toInstance(appConfig.getUiConfig().getUrlConfig());
        bind(VolumeConfig.class).toInstance(appConfig.getVolumeConfig());
        bind(XsltConfig.class).toInstance(appConfig.getPipelineConfig().getXsltConfig());
        bind(stroom.activity.impl.db.ActivityConfig.class).toInstance(appConfig.getActivityConfig());
        bind(stroom.statistics.impl.sql.search.SearchConfig.class).toInstance(appConfig.getStatisticsConfig().getSqlStatisticsConfig().getSearchConfig());
        bind(stroom.ui.config.shared.ProcessConfig.class).toInstance(appConfig.getUiConfig().getProcessConfig());
    }

}
