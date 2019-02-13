package stroom.config.app;

import com.google.inject.AbstractModule;
import stroom.benchmark.BenchmarkClusterConfig;
import stroom.cluster.api.ClusterConfig;
import stroom.dashboard.QueryHistoryConfig;
import stroom.meta.impl.db.MetaServiceConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.receive.ReceiveDataConfig;
import stroom.datasource.DataSourceUrlConfig;
import stroom.explorer.impl.db.ExplorerConfig;
import stroom.importexport.impl.ContentPackImportConfig;
import stroom.importexport.impl.ExportConfig;
import stroom.job.JobSystemConfig;
import stroom.lifecycle.impl.LifecycleConfig;
import stroom.node.impl.HeapHistogramConfig;
import stroom.node.impl.NodeConfig;
import stroom.node.impl.StatusConfig;
import stroom.persist.CoreConfig;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.refdata.store.RefDataStoreConfig;
import stroom.data.retention.impl.DataRetentionConfig;
import stroom.search.SearchConfig;
import stroom.search.extraction.ExtractionConfig;
import stroom.search.shard.IndexShardSearchConfig;
import stroom.security.AuthenticationConfig;
import stroom.security.SecurityConfig;
import stroom.servicediscovery.ServiceDiscoveryConfig;
import stroom.statistics.StatisticsConfig;
import stroom.statistics.internal.InternalStatisticsConfig;
import stroom.statistics.sql.SQLStatisticsConfig;
import stroom.statistics.stroomstats.internal.HBaseStatisticsConfig;
import stroom.streamtask.ProcessConfig;
import stroom.streamtask.ProxyAggregationConfig;
import stroom.ui.config.shared.ActivityConfig;
import stroom.ui.config.shared.QueryConfig;
import stroom.ui.config.shared.SplashConfig;
import stroom.ui.config.shared.ThemeConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;
import stroom.util.io.PathConfig;
import stroom.volume.VolumeConfig;

public class AppConfigModule extends AbstractModule {
    private final AppConfig appConfig;

    public AppConfigModule(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    protected void configure() {
        // Bind the application config.        
        bind(AppConfig.class).toInstance(appConfig);

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own
        bind(stroom.activity.impl.db.ActivityConfig.class).toInstance(appConfig.getActivityConfig());
        bind(ActivityConfig.class).toInstance(appConfig.getUiConfig().getActivityConfig());
        bind(AppenderConfig.class).toInstance(appConfig.getPipelineConfig().getAppenderConfig());
        bind(AuthenticationConfig.class).toInstance(appConfig.getSecurityConfig().getAuthenticationConfig());
        bind(BenchmarkClusterConfig.class).toInstance(appConfig.getBenchmarkClusterConfig());
        bind(ClusterConfig.class).toInstance(appConfig.getClusterConfig());
        bind(ContentPackImportConfig.class).toInstance(appConfig.getContentPackImportConfig());
        bind(CoreConfig.class).toInstance(appConfig.getCoreConfig());
        bind(DataConfig.class).toInstance(appConfig.getDataConfig());
        bind(ReceiveDataConfig.class).toInstance(appConfig.getDataFeedConfig());
        bind(MetaServiceConfig.class).toInstance(appConfig.getDataConfig().getDataMetaServiceConfig());
        bind(DataSourceUrlConfig.class).toInstance(appConfig.getDataSourceUrlConfig());
        bind(DataStoreServiceConfig.class).toInstance(appConfig.getDataConfig().getDataStoreServiceConfig());
        bind(ExplorerConfig.class).toInstance(appConfig.getExplorerConfig());
        bind(ExportConfig.class).toInstance(appConfig.getExportConfig());
        bind(ExtractionConfig.class).toInstance(appConfig.getSearchConfig().getExtractionConfig());
        bind(HBaseStatisticsConfig.class).toInstance(appConfig.getStatisticsConfig().getHbaseStatisticsConfig());
        bind(HeapHistogramConfig.class).toInstance(appConfig.getNodeConfig().getStatusConfig().getHeapHistogramConfig());
        bind(IndexShardSearchConfig.class).toInstance(appConfig.getSearchConfig().getShardConfig());
        bind(InternalStatisticsConfig.class).toInstance(appConfig.getStatisticsConfig().getInternalStatisticsConfig());
        bind(JobSystemConfig.class).toInstance(appConfig.getJobSystemConfig());
        bind(LifecycleConfig.class).toInstance(appConfig.getLifecycleConfig());
        bind(NodeConfig.class).toInstance(appConfig.getNodeConfig());
        bind(PathConfig.class).toInstance(appConfig.getPathConfig());
        bind(PipelineConfig.class).toInstance(appConfig.getPipelineConfig());
        bind(DataRetentionConfig.class).toInstance(appConfig.getPolicyConfig());
        bind(ProcessConfig.class).toInstance(appConfig.getProcessConfig());
        bind(PropertyServiceConfig.class).toInstance(appConfig.getPropertyServiceConfig());
        bind(ProxyAggregationConfig.class).toInstance(appConfig.getProxyAggregationConfig());
        bind(QueryConfig.class).toInstance(appConfig.getUiConfig().getQueryConfig());
        bind(QueryHistoryConfig.class).toInstance(appConfig.getQueryHistoryConfig());
        bind(RefDataStoreConfig.class).toInstance(appConfig.getPipelineConfig().getRefDataStoreConfig());
        bind(SQLStatisticsConfig.class).toInstance(appConfig.getStatisticsConfig().getSqlStatisticsConfig());
        bind(SearchConfig.class).toInstance(appConfig.getSearchConfig());
        bind(SecurityConfig.class).toInstance(appConfig.getSecurityConfig());
        bind(ServiceDiscoveryConfig.class).toInstance(appConfig.getServiceDiscoveryConfig());
        bind(SplashConfig.class).toInstance(appConfig.getUiConfig().getSplashConfig());
        bind(StatisticsConfig.class).toInstance(appConfig.getStatisticsConfig());
        bind(StatusConfig.class).toInstance(appConfig.getNodeConfig().getStatusConfig());
        bind(ThemeConfig.class).toInstance(appConfig.getUiConfig().getThemeConfig());
        bind(UiConfig.class).toInstance(appConfig.getUiConfig());
        bind(UrlConfig.class).toInstance(appConfig.getUiConfig().getUrlConfig());
        bind(VolumeConfig.class).toInstance(appConfig.getVolumeConfig());
        bind(XsltConfig.class).toInstance(appConfig.getPipelineConfig().getXsltConfig());
        bind(stroom.statistics.sql.search.SearchConfig.class).toInstance(appConfig.getStatisticsConfig().getSqlStatisticsConfig().getSearchConfig());
        bind(stroom.ui.config.shared.ProcessConfig.class).toInstance(appConfig.getUiConfig().getProcessConfig());
    }
}
