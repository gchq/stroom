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

package stroom.app.client.gin;

import stroom.about.client.gin.AboutGinjector;
import stroom.about.client.gin.AboutModule;
import stroom.activity.client.ActivityModule;
import stroom.ai.client.gin.AskStroomAIGinjector;
import stroom.ai.client.gin.AskStroomAIModule;
import stroom.alert.client.gin.AlertGinjector;
import stroom.alert.client.gin.AlertModule;
import stroom.analytics.client.gin.AnalyticsGinjector;
import stroom.analytics.client.gin.AnalyticsModule;
import stroom.analytics.client.gin.ReportGinjector;
import stroom.analytics.client.gin.ReportModule;
import stroom.annotation.client.gin.AnnotationGinjector;
import stroom.annotation.client.gin.AnnotationModule;
import stroom.aws.s3.client.gin.S3ConfigGinjector;
import stroom.aws.s3.client.gin.S3ConfigModule;
import stroom.cache.client.gin.CacheGinjector;
import stroom.cache.client.gin.CacheModule;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.contentstore.client.gin.ContentStoreModule;
import stroom.core.client.presenter.CorePresenter;
import stroom.core.client.presenter.FullScreenPresenter;
import stroom.credentials.client.gin.CredentialsModule;
import stroom.dashboard.client.embeddedquery.gin.EmbeddedQueryGinjector;
import stroom.dashboard.client.embeddedquery.gin.EmbeddedQueryModule;
import stroom.dashboard.client.gin.DashboardGinjector;
import stroom.dashboard.client.gin.DashboardModule;
import stroom.dashboard.client.query.gin.QueryGinjector;
import stroom.dashboard.client.vis.gin.VisGinjector;
import stroom.dashboard.client.vis.gin.VisModule;
import stroom.data.client.gin.StreamStoreGinjector;
import stroom.data.client.gin.StreamStoreModule;
import stroom.data.store.impl.fs.client.gin.FsVolumeGinjector;
import stroom.data.store.impl.fs.client.gin.FsVolumeModule;
import stroom.dictionary.client.gin.DictionaryGinjector;
import stroom.dictionary.client.gin.DictionaryModule;
import stroom.dispatch.client.RestModule;
import stroom.documentation.client.gin.DocumentationGinjector;
import stroom.documentation.client.gin.DocumentationModule;
import stroom.entity.client.gin.EntityGinjector;
import stroom.entity.client.gin.EntityModule;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter;
import stroom.explorer.client.presenter.FindInContentPresenter;
import stroom.explorer.client.presenter.FindPresenter;
import stroom.explorer.client.presenter.NavigationPresenter;
import stroom.explorer.client.presenter.RecentItemsPresenter;
import stroom.feed.client.gin.FeedGinjector;
import stroom.feed.client.gin.FeedModule;
import stroom.folder.client.gin.FolderGinjector;
import stroom.folder.client.gin.FolderModule;
import stroom.gitrepo.client.gin.GitRepoGinjector;
import stroom.gitrepo.client.gin.GitRepoModule;
import stroom.http.client.gin.HttpGinjector;
import stroom.http.client.gin.HttpModule;
import stroom.importexport.client.gin.ImportExportConfigGinjector;
import stroom.importexport.client.gin.ImportExportConfigModule;
import stroom.index.client.gin.IndexGinjector;
import stroom.index.client.gin.IndexModule;
import stroom.kafka.client.gin.KafkaConfigGinjector;
import stroom.kafka.client.gin.KafkaConfigModule;
import stroom.main.client.presenter.MainPresenter;
import stroom.monitoring.client.gin.MonitoringGinjector;
import stroom.monitoring.client.gin.MonitoringModule;
import stroom.openai.client.gin.OpenAIModelGinjector;
import stroom.openai.client.gin.OpenAIModelModule;
import stroom.pathways.client.gin.PathwaysGinjector;
import stroom.pathways.client.gin.PathwaysModule;
import stroom.pipeline.client.gin.PipelineGinjector;
import stroom.pipeline.client.gin.PipelineModule;
import stroom.planb.client.gin.PlanBGinjector;
import stroom.planb.client.gin.PlanBModule;
import stroom.preferences.client.gin.UserPreferencesGinjector;
import stroom.preferences.client.gin.UserPreferencesModule;
import stroom.query.client.gin.QueryModule;
import stroom.receive.content.client.gin.ContentTemplateGinjector;
import stroom.receive.content.client.gin.ContentTemplateModule;
import stroom.receive.rules.client.gin.PolicyModule;
import stroom.script.client.gin.ScriptGinjector;
import stroom.script.client.gin.ScriptModule;
import stroom.search.elastic.client.gin.ElasticClusterGinjector;
import stroom.search.elastic.client.gin.ElasticClusterModule;
import stroom.search.elastic.client.gin.ElasticIndexGinjector;
import stroom.search.elastic.client.gin.ElasticIndexModule;
import stroom.search.solr.client.gin.SolrIndexGinjector;
import stroom.search.solr.client.gin.SolrIndexModule;
import stroom.security.client.gin.SecurityGinjector;
import stroom.security.client.gin.SecurityModule;
import stroom.security.identity.client.gin.ChangePasswordGinjector;
import stroom.security.identity.client.gin.ChangePasswordModule;
import stroom.state.client.gin.ScyllaDbGinjector;
import stroom.state.client.gin.ScyllaDbModule;
import stroom.state.client.gin.StateStoreGinjector;
import stroom.state.client.gin.StateStoreModule;
import stroom.statistics.impl.hbase.client.gin.StroomStatsStoreGinjector;
import stroom.statistics.impl.hbase.client.gin.StroomStatsStoreModule;
import stroom.statistics.impl.sql.client.gin.StatisticsGinjector;
import stroom.statistics.impl.sql.client.gin.StatisticsModule;
import stroom.task.client.gin.TaskGinjector;
import stroom.task.client.gin.TaskModule;
import stroom.view.client.gin.ViewGinjector;
import stroom.view.client.gin.ViewModule;
import stroom.visualisation.client.gin.VisualisationGinjector;
import stroom.visualisation.client.gin.VisualisationModule;
import stroom.welcome.client.gin.WelcomeGinjector;
import stroom.welcome.client.gin.WelcomeModule;
import stroom.widget.popup.client.gin.PopupGinjector;
import stroom.widget.popup.client.gin.PopupModule;
import stroom.xmlschema.client.gin.XMLSchemaGinjector;
import stroom.xmlschema.client.gin.XMLSchemaModule;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.proxy.PlaceManager;

@GinModules({
        AboutModule.class,
        ActivityModule.class,
        AlertModule.class,
        AnnotationModule.class,
        AppModule.class,
        AskStroomAIModule.class,
        ContentStoreModule.class,
        CacheModule.class,
        ContentTemplateModule.class,
        CredentialsModule.class,
        RestModule.class,
        DashboardModule.class,
        DictionaryModule.class,
        DocumentationModule.class,
        EntityModule.class,
        FsVolumeModule.class,
        FeedModule.class,
        FolderModule.class,
        HttpModule.class,
        ImportExportConfigModule.class,
        IndexModule.class,
        KafkaConfigModule.class,
        MonitoringModule.class,
        OpenAIModelModule.class,
        PathwaysModule.class,
        PipelineModule.class,
        PluginsModule.class,
        PolicyModule.class,
        PopupModule.class,
        UserPreferencesModule.class,
        QueryModule.class,
        ScriptModule.class,
        SecurityModule.class,
        ChangePasswordModule.class,
        ElasticClusterModule.class,
        ElasticIndexModule.class,
        AnalyticsModule.class,
        ReportModule.class,
        SolrIndexModule.class,
        StatisticsModule.class,
        StreamStoreModule.class,
        StroomStatsStoreModule.class,
        TaskModule.class,
        VisModule.class,
        EmbeddedQueryModule.class,
        VisualisationModule.class,
        ViewModule.class,
        WelcomeModule.class,
        XMLSchemaModule.class,
        S3ConfigModule.class,
        ScyllaDbModule.class,
        StateStoreModule.class,
        PlanBModule.class,
        GitRepoModule.class
})
public interface AppGinjectorUser extends
        AboutGinjector,
        AlertGinjector,
        AnnotationGinjector,
        AskStroomAIGinjector,
        CacheGinjector,
        ContentTemplateGinjector,
        DashboardGinjector,
        DictionaryGinjector,
        DocumentationGinjector,
        EntityGinjector,
        FsVolumeGinjector,
        FeedGinjector,
        FolderGinjector,
        Ginjector,
        HttpGinjector,
        ImportExportConfigGinjector,
        IndexGinjector,
        KafkaConfigGinjector,
        MonitoringGinjector,
        OpenAIModelGinjector,
        PathwaysGinjector,
        PipelineGinjector,
        PluginsGinjector,
        PopupGinjector,
        QueryGinjector,
        UserPreferencesGinjector,
        ScriptGinjector,
        SecurityGinjector,
        ChangePasswordGinjector,
        AnalyticsGinjector,
        ElasticClusterGinjector,
        ElasticIndexGinjector,
        ReportGinjector,
        SolrIndexGinjector,
        StatisticsGinjector,
        StreamStoreGinjector,
        StroomStatsStoreGinjector,
        TaskGinjector,
        ViewGinjector,
        VisGinjector,
        EmbeddedQueryGinjector,
        VisualisationGinjector,
        WelcomeGinjector,
        XMLSchemaGinjector,
        S3ConfigGinjector,
        ScyllaDbGinjector,
        StateStoreGinjector,
        PlanBGinjector,
        GitRepoGinjector /*,
        CredentialsGinjector*/ {

    // Default implementation of standard resources
    EventBus getEventBus();

    PlaceManager getPlaceManager();

    // Presenters
    Provider<CorePresenter> getCorePresenter();

    AsyncProvider<MainPresenter> getMainPresenter();

    AsyncProvider<NavigationPresenter> getExplorerTabPanePresenter();

    AsyncProvider<ContentTabPanePresenter> getContentTabPanePresenter();

    AsyncProvider<FindPresenter> getFindPresenter();

    AsyncProvider<RecentItemsPresenter> getRecentItemsPresenter();

    AsyncProvider<FindInContentPresenter> getFindInContentPresenter();

    Provider<FullScreenPresenter> getFullScreenPresenter();

    AsyncProvider<ExplorerNodeEditTagsPresenter> getExplorerNodeEditPresenter();

    AsyncProvider<ExplorerNodeRemoveTagsPresenter> getExplorerNodeRemovePresenter();
}
