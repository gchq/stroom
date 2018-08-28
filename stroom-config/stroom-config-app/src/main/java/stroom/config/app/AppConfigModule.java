package stroom.config.app;

import com.google.inject.AbstractModule;
import stroom.benchmark.BenchmarkClusterConfig;
import stroom.cluster.ClusterConfig;
import stroom.dashboard.QueryHistoryConfig;
import stroom.data.meta.impl.db.DataMetaServiceConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.datafeed.DataFeedConfig;
import stroom.datasource.DataSourceUrlConfig;
import stroom.explorer.impl.db.ExplorerConfig;
import stroom.importexport.ContentPackImportConfig;
import stroom.lifecycle.LifecycleConfig;
import stroom.node.HeapHistogramConfig;
import stroom.node.NodeConfig;
import stroom.node.StatusConfig;
import stroom.persist.CoreConfig;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XsltConfig;
import stroom.policy.PolicyConfig;
import stroom.refdata.store.RefDataStoreConfig;
import stroom.search.SearchConfig;
import stroom.search.extraction.ExtractionConfig;
import stroom.search.shard.IndexShardSearchConfig;
import stroom.security.AuthenticationConfig;
import stroom.security.SecurityConfig;
import stroom.servicediscovery.ServiceDiscoveryConfig;
import stroom.servlet.ExportConfig;
import stroom.statistics.StatisticsConfig;
import stroom.statistics.internal.InternalStatisticsConfig;
import stroom.statistics.sql.SQLStatisticsConfig;
import stroom.statistics.stroomstats.internal.HBaseStatisticsConfig;
import stroom.streamtask.ProcessConfig;
import stroom.streamtask.ProxyAggregationConfig;
import stroom.ui.config.shared.QueryConfig;
import stroom.ui.config.shared.ThemeConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;
import stroom.volume.VolumeConfig;

public class AppConfigModule extends AbstractModule {
    private final AppConfig appConfig;

    public AppConfigModule(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    protected void configure() {
        // Bind the application config.        
        bind(AppConfig.class).toProvider(() -> appConfig);

        bind(BenchmarkClusterConfig.class).toProvider(appConfig::getBenchmarkClusterConfig);
        bind(ClusterConfig.class).toProvider(appConfig::getClusterConfig);
        bind(ContentPackImportConfig.class).toProvider(appConfig::getContentPackImportConfig);
        bind(CoreConfig.class).toProvider(appConfig::getCoreConfig);
        bind(DataConfig.class).toProvider(appConfig::getDataConfig);
        bind(DataMetaServiceConfig.class).toProvider(() -> appConfig.getDataConfig().getDataMetaServiceConfig());
        bind(DataStoreServiceConfig.class).toProvider(() -> appConfig.getDataConfig().getDataStoreServiceConfig());
        bind(DataFeedConfig.class).toProvider(appConfig::getDataFeedConfig);
        bind(DataSourceUrlConfig.class).toProvider(appConfig::getDataSourceUrlConfig);
        bind(ExplorerConfig.class).toProvider(appConfig::getExplorerConfig);
        bind(ExportConfig.class).toProvider(appConfig::getExportConfig);
        bind(LifecycleConfig.class).toProvider(appConfig::getLifecycleConfig);
        bind(NodeConfig.class).toProvider(appConfig::getNodeConfig);
        bind(StatusConfig.class).toProvider(() -> appConfig.getNodeConfig().getStatusConfig());
        bind(HeapHistogramConfig.class).toProvider(() -> appConfig.getNodeConfig().getStatusConfig().getHeapHistogramConfig());
        bind(PipelineConfig.class).toProvider(appConfig::getPipelineConfig);
        bind(AppenderConfig.class).toProvider(() -> appConfig.getPipelineConfig().getAppenderConfig());
        bind(XsltConfig.class).toProvider(() -> appConfig.getPipelineConfig().getXsltConfig());
        bind(PolicyConfig.class).toProvider(appConfig::getPolicyConfig);
        bind(ProcessConfig.class).toProvider(appConfig::getProcessConfig);
        bind(PropertyServiceConfig.class).toProvider(appConfig::getPropertyServiceConfig);
        bind(ProxyAggregationConfig.class).toProvider(appConfig::getProxyAggregationConfig);
        bind(QueryHistoryConfig.class).toProvider(appConfig::getQueryHistoryConfig);
        bind(RefDataStoreConfig.class).toProvider(appConfig::getRefDataStoreConfig);
        bind(SearchConfig.class).toProvider(appConfig::getSearchConfig);
        bind(ExtractionConfig.class).toProvider(() -> appConfig.getSearchConfig().getExtractionConfig());
        bind(IndexShardSearchConfig.class).toProvider(() -> appConfig.getSearchConfig().getShardConfig());
        bind(SecurityConfig.class).toProvider(appConfig::getSecurityConfig);
        bind(AuthenticationConfig.class).toProvider(() -> appConfig.getSecurityConfig().getAuthenticationConfig());
        bind(ServiceDiscoveryConfig.class).toProvider(appConfig::getServiceDiscoveryConfig);
        bind(StatisticsConfig.class).toProvider(appConfig::getStatisticsConfig);
        bind(HBaseStatisticsConfig.class).toProvider(() -> appConfig.getStatisticsConfig().getHbaseStatisticsConfig());
        bind(InternalStatisticsConfig.class).toProvider(() -> appConfig.getStatisticsConfig().getInternalStatisticsConfig());
        bind(SQLStatisticsConfig.class).toProvider(() -> appConfig.getStatisticsConfig().getSqlStatisticsConfig());
        bind(stroom.statistics.sql.search.SearchConfig.class).toProvider(() -> appConfig.getStatisticsConfig().getSqlStatisticsConfig().getSearchConfig());
        bind(UiConfig.class).toProvider(appConfig::getUiConfig);
        bind(stroom.ui.config.shared.ProcessConfig.class).toProvider(() -> appConfig.getUiConfig().getProcessConfig());
        bind(ThemeConfig.class).toProvider(() -> appConfig.getUiConfig().getThemeConfig());
        bind(QueryConfig.class).toProvider(() -> appConfig.getUiConfig().getQueryConfig());
        bind(UrlConfig.class).toProvider(() -> appConfig.getUiConfig().getUrlConfig());
        bind(VolumeConfig.class).toProvider(appConfig::getVolumeConfig);
    }
}
