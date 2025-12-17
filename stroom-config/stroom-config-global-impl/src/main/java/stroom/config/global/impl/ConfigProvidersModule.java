/*
 * Copyright 2016-2025 Crown Copyright
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
    stroom.ai.shared.AskStroomAIConfig getAskStroomAIConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ai.shared.AskStroomAIConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ai.shared.ChatMemoryConfig getChatMemoryConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ai.shared.ChatMemoryConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.ai.shared.TableSummaryConfig getTableSummaryConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ai.shared.TableSummaryConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.analytics.impl.AnalyticsConfig getAnalyticsConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.analytics.impl.AnalyticsConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.analytics.impl.EmailConfig getEmailConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.analytics.impl.EmailConfig.class);
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
    stroom.aws.s3.impl.S3Config getS3Config(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.aws.s3.impl.S3Config.class);
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
    stroom.config.app.CrossModuleConfig getCrossModuleConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.CrossModuleConfig.class);
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
    stroom.config.app.SessionConfig getSessionConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.config.app.SessionConfig.class);
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
    stroom.contentstore.impl.ContentStoreConfig getContentStoreConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.contentstore.impl.ContentStoreConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.core.receive.AutoContentCreationConfig getAutoContentCreationConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.core.receive.AutoContentCreationConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.credentials.impl.CredentialsConfig getCredentialsConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.credentials.impl.CredentialsConfig.class);
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
    stroom.gitrepo.api.GitRepoConfig getGitRepoConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.gitrepo.api.GitRepoConfig.class);
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
    stroom.index.impl.IndexConfig getIndexConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.IndexConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.index.impl.IndexFieldDbConfig getIndexFieldDbConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.IndexFieldDbConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.index.impl.IndexShardSearchConfig getIndexShardSearchConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.IndexShardSearchConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.index.impl.IndexShardWriterCacheConfig getIndexShardWriterCacheConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.index.impl.IndexShardWriterCacheConfig.class);
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
    stroom.pipeline.refdata.ReferenceDataStagingLmdbConfig getReferenceDataStagingLmdbConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.pipeline.refdata.ReferenceDataStagingLmdbConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.planb.impl.PlanBConfig getPlanBConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.planb.impl.PlanBConfig.class);
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
    stroom.query.common.v2.AnalyticResultStoreConfig getAnalyticResultStoreConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.query.common.v2.AnalyticResultStoreConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.query.common.v2.DuplicateCheckStoreConfig getDuplicateCheckStoreConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.query.common.v2.DuplicateCheckStoreConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.query.common.v2.SearchResultStoreConfig getSearchResultStoreConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.query.common.v2.SearchResultStoreConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.receive.common.ReceiveDataConfig getReceiveDataConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.receive.common.ReceiveDataConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.receive.rules.impl.StroomReceiptPolicyConfig getStroomReceiptPolicyConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.receive.rules.impl.StroomReceiptPolicyConfig.class);
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
    stroom.search.elastic.ElasticClientConfig getElasticClientConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.elastic.ElasticClientConfig.class);
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
    stroom.search.elastic.suggest.ElasticSuggestConfig getElasticSuggestConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.search.elastic.suggest.ElasticSuggestConfig.class);
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
    stroom.security.common.impl.ContentSecurityConfig getContentSecurityConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.common.impl.ContentSecurityConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.EmailConfig getEmailConfig2(
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
    stroom.security.impl.StroomOpenIdConfig getStroomOpenIdConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.impl.StroomOpenIdConfig.class);
    }

    // Binding StroomOpenIdConfig to additional interface AbstractOpenIdConfig
    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.openid.api.AbstractOpenIdConfig getAbstractOpenIdConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.security.impl.StroomOpenIdConfig.class);
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.state.impl.StateConfig getStateConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.state.impl.StateConfig.class);
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
    stroom.ui.config.shared.AnalyticUiDefaultConfig getAnalyticUiDefaultConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.AnalyticUiDefaultConfig.class);
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
    stroom.ui.config.shared.NodeMonitoringConfig getNodeMonitoringConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.NodeMonitoringConfig.class);
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
    stroom.ui.config.shared.ReportUiDefaultConfig getReportUiDefaultConfig(
            final ConfigMapper configMapper) {
        return configMapper.getConfigObject(
                stroom.ui.config.shared.ReportUiDefaultConfig.class);
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
    stroom.analytics.impl.SmtpConfig getSmtpConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.analytics.impl.SmtpConfig cannot be injected directly. "
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
    stroom.query.common.v2.AbstractResultStoreConfig getAbstractResultStoreConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.query.common.v2.AbstractResultStoreConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.query.common.v2.ResultStoreLmdbConfig getResultStoreLmdbConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.query.common.v2.ResultStoreLmdbConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.query.common.v2.ResultStoreMapConfig getResultStoreMapConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.query.common.v2.ResultStoreMapConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.identity.config.SmtpConfig getSmtpConfig2ButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.security.identity.config.SmtpConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.config.global.impl.GenerateConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.net.UriConfig getUriConfigButThrow(
            final ConfigMapper configMapper) {
        throw new UnsupportedOperationException(
                "stroom.util.net.UriConfig cannot be injected directly. "
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
