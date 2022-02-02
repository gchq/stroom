package stroom.config.global.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.annotation.processing.Generated;

/**
 * IMPORTANT - This whole file is generated using
 * {@link stroom.config.global.impl.GenerateConfigProvidersModule}
 * DO NOT edit it directly
 */
@Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
public class ConfigProvidersModule extends AbstractModule {

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.RepoConfig getRepoConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.core.receive.ProxyAggregationConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.RepoDbConfig getRepoDbConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.core.receive.ProxyAggregationRepoDbConfig.class);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.activity.impl.db.ActivityConfig getActivityConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.activity.impl.db.ActivityConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.alert.impl.AlertConfig getAlertConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.alert.impl.AlertConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.annotation.impl.AnnotationConfig getAnnotationConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.annotation.impl.AnnotationConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.bytebuffer.ByteBufferPoolConfig getByteBufferPoolConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.bytebuffer.ByteBufferPoolConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.cluster.api.ClusterConfig getClusterConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.cluster.api.ClusterConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.cluster.lock.impl.db.ClusterLockConfig getClusterLockConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.cluster.lock.impl.db.ClusterLockConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.app.AppConfig getAppConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.AppConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.app.DataConfig getDataConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.DataConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.app.PropertyServiceConfig getPropertyServiceConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.PropertyServiceConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.app.SecurityConfig getSecurityConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.SecurityConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.app.SessionCookieConfig getSessionCookieConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.SessionCookieConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.app.StatisticsConfig getStatisticsConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.StatisticsConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.common.NodeUriConfig getNodeUriConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.common.NodeUriConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.common.PublicUriConfig getPublicUriConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.common.PublicUriConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.common.UiUriConfig getUiUriConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.common.UiUriConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.core.receive.ProxyAggregationConfig getProxyAggregationConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.core.receive.ProxyAggregationConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.core.receive.ProxyAggregationRepoDbConfig getProxyAggregationRepoDbConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.core.receive.ProxyAggregationRepoDbConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.core.receive.ReceiveDataConfig getReceiveDataConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.core.receive.ReceiveDataConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.dashboard.impl.DashboardConfig getDashboardConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.dashboard.impl.DashboardConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.dashboard.impl.datasource.DataSourceUrlConfig getDataSourceUrlConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.dashboard.impl.datasource.DataSourceUrlConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.data.retention.api.DataRetentionConfig getDataRetentionConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.data.retention.api.DataRetentionConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.data.store.impl.fs.DataStoreServiceConfig getDataStoreServiceConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.data.store.impl.fs.DataStoreServiceConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.data.store.impl.fs.FsVolumeConfig getFsVolumeConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.data.store.impl.fs.FsVolumeConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.docstore.impl.db.DocStoreConfig getDocStoreConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.docstore.impl.db.DocStoreConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.event.logging.impl.LoggingConfig getLoggingConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.event.logging.impl.LoggingConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.explorer.impl.ExplorerConfig getExplorerConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.explorer.impl.ExplorerConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.feed.impl.FeedConfig getFeedConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.feed.impl.FeedConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.importexport.impl.ContentPackImportConfig getContentPackImportConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.importexport.impl.ContentPackImportConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.importexport.impl.ExportConfig getExportConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.importexport.impl.ExportConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.index.impl.IndexCacheConfig getIndexCacheConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.IndexCacheConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.index.impl.IndexConfig getIndexConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.IndexConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.index.impl.IndexWriterConfig getIndexWriterConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.IndexWriterConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.index.impl.selection.VolumeConfig getVolumeConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.selection.VolumeConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.job.impl.JobSystemConfig getJobSystemConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.job.impl.JobSystemConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.kafka.impl.KafkaConfig getKafkaConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.kafka.impl.KafkaConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.legacy.db.LegacyConfig getLegacyConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.legacy.db.LegacyConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.lifecycle.impl.LifecycleConfig getLifecycleConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.lifecycle.impl.LifecycleConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.lmdb.LmdbLibraryConfig getLmdbLibraryConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.lmdb.LmdbLibraryConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.meta.impl.MetaServiceConfig getMetaServiceConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.meta.impl.MetaServiceConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.meta.impl.MetaValueConfig getMetaValueConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.meta.impl.MetaValueConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.node.impl.HeapHistogramConfig getHeapHistogramConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.node.impl.HeapHistogramConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.node.impl.NodeConfig getNodeConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.node.impl.NodeConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.node.impl.StatusConfig getStatusConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.node.impl.StatusConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.pipeline.PipelineConfig getPipelineConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.pipeline.PipelineConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.pipeline.destination.AppenderConfig getAppenderConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.pipeline.destination.AppenderConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.pipeline.filter.XmlSchemaConfig getXmlSchemaConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.pipeline.filter.XmlSchemaConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.pipeline.filter.XsltConfig getXsltConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.pipeline.filter.XsltConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.pipeline.refdata.ReferenceDataConfig getReferenceDataConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.pipeline.refdata.ReferenceDataConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.pipeline.refdata.ReferenceDataLmdbConfig getReferenceDataLmdbConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.pipeline.refdata.ReferenceDataLmdbConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.processor.impl.ProcessorConfig getProcessorConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.processor.impl.ProcessorConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.AggregatorConfig getAggregatorConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.proxy.repo.AggregatorConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.query.common.v2.ResultStoreConfig getResultStoreConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.query.common.v2.ResultStoreConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.query.common.v2.ResultStoreLmdbConfig getResultStoreLmdbConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.query.common.v2.ResultStoreLmdbConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.elastic.CryptoConfig getCryptoConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.elastic.CryptoConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.elastic.ElasticConfig getElasticConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.elastic.ElasticConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.elastic.indexing.ElasticIndexingConfig getElasticIndexingConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.elastic.indexing.ElasticIndexingConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.elastic.search.ElasticSearchConfig getElasticSearchConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.elastic.search.ElasticSearchConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.extraction.ExtractionConfig getExtractionConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.extraction.ExtractionConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.impl.SearchConfig getSearchConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.impl.SearchConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.impl.shard.IndexShardSearchConfig getIndexShardSearchConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.impl.shard.IndexShardSearchConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.solr.SolrConfig getSolrConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.solr.SolrConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.search.solr.search.SolrSearchConfig getSolrSearchConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.solr.search.SolrSearchConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.searchable.impl.SearchableConfig getSearchableConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.searchable.impl.SearchableConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.EmailConfig getEmailConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.identity.config.EmailConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.IdentityConfig getIdentityConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.identity.config.IdentityConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.OpenIdConfig getOpenIdConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.identity.config.OpenIdConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.PasswordPolicyConfig getPasswordPolicyConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.identity.config.PasswordPolicyConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.TokenConfig getTokenConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.identity.config.TokenConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.impl.AuthenticationConfig getAuthenticationConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.impl.AuthenticationConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.impl.AuthorisationConfig getAuthorisationConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.impl.AuthorisationConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.impl.ContentSecurityConfig getContentSecurityConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.impl.ContentSecurityConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.impl.OpenIdConfig getOpenIdConfig2(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.impl.OpenIdConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.servicediscovery.impl.ServiceDiscoveryConfig getServiceDiscoveryConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.servicediscovery.impl.ServiceDiscoveryConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.statistics.impl.InternalStatisticsConfig getInternalStatisticsConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.statistics.impl.InternalStatisticsConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig getHBaseStatisticsConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.statistics.impl.hbase.internal.KafkaTopicsConfig getKafkaTopicsConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.statistics.impl.hbase.internal.KafkaTopicsConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.statistics.impl.sql.SQLStatisticsConfig getSQLStatisticsConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.statistics.impl.sql.SQLStatisticsConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.statistics.impl.sql.search.SearchConfig getSearchConfig2(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.statistics.impl.sql.search.SearchConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.storedquery.impl.StoredQueryConfig getStoredQueryConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.storedquery.impl.StoredQueryConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.ActivityConfig getActivityConfig2(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.ActivityConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.InfoPopupConfig getInfoPopupConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.InfoPopupConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.ProcessConfig getProcessConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.ProcessConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.QueryConfig getQueryConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.QueryConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.SourceConfig getSourceConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.SourceConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.SplashConfig getSplashConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.SplashConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.ThemeConfig getThemeConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.ThemeConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ui.config.shared.UiConfig getUiConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.UiConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.xml.ParserConfig getParserConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.util.xml.ParserConfig.class);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.SmtpConfig getSmtpConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.security.identity.config.SmtpConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.common.AbstractDbConfig getAbstractDbConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.config.common.AbstractDbConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.common.ConnectionConfig getConnectionConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.config.common.ConnectionConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.common.ConnectionPoolConfig getConnectionPoolConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.config.common.ConnectionPoolConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.config.common.UriConfig getUriConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.config.common.UriConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.cache.CacheConfig getCacheConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.util.cache.CacheConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.shared.AbstractConfig getAbstractConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.util.shared.AbstractConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

}
