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
 *
 */

package stroom.app.client.gin;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import stroom.about.client.presenter.AboutPresenter;
import stroom.activity.client.ActivityModule;
import stroom.alert.client.gin.AlertGinjector;
import stroom.alert.client.gin.AlertModule;
import stroom.cache.client.gin.CacheGinjector;
import stroom.cache.client.gin.CacheModule;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.core.client.presenter.CorePresenter;
import stroom.dashboard.client.gin.DashboardGinjector;
import stroom.dashboard.client.gin.DashboardModule;
import stroom.dashboard.client.vis.gin.VisGinjector;
import stroom.dashboard.client.vis.gin.VisModule;
import stroom.data.client.gin.StreamStoreGinjector;
import stroom.data.client.gin.StreamStoreModule;
import stroom.data.store.impl.fs.client.gin.FSVolumeGinjector;
import stroom.data.store.impl.fs.client.gin.FSVolumeModule;
import stroom.dictionary.client.gin.DictionaryGinjector;
import stroom.dictionary.client.gin.DictionaryModule;
import stroom.dispatch.client.ClientDispatchModule;
import stroom.document.client.gin.NewUiGinjector;
import stroom.document.client.gin.NewUiModule;
import stroom.entity.client.gin.EntityGinjector;
import stroom.entity.client.gin.EntityModule;
import stroom.explorer.client.presenter.ExplorerTabPanePresenter;
import stroom.explorer.client.presenter.ExplorerTreePresenter;
import stroom.feed.client.gin.FeedGinjector;
import stroom.feed.client.gin.FeedModule;
import stroom.folder.client.gin.FolderGinjector;
import stroom.folder.client.gin.FolderModule;
import stroom.importexport.client.gin.ImportExportConfigGinjector;
import stroom.importexport.client.gin.ImportExportConfigModule;
import stroom.index.client.gin.IndexGinjector;
import stroom.index.client.gin.IndexModule;
import stroom.kafkaConfig.client.gin.KafkaConfigGinjector;
import stroom.kafkaConfig.client.gin.KafkaConfigModule;
import stroom.main.client.presenter.MainPresenter;
import stroom.menubar.client.presenter.MenubarPresenter;
import stroom.monitoring.client.gin.MonitoringGinjector;
import stroom.monitoring.client.gin.MonitoringModule;
import stroom.pipeline.client.gin.PipelineGinjector;
import stroom.pipeline.client.gin.PipelineModule;
import stroom.query.client.QueryModule;
import stroom.receive.rules.client.gin.PolicyModule;
import stroom.script.client.gin.ScriptGinjector;
import stroom.script.client.gin.ScriptModule;
import stroom.search.solr.client.gin.SolrIndexGinjector;
import stroom.search.solr.client.gin.SolrIndexModule;
import stroom.security.client.gin.SecurityGinjector;
import stroom.security.client.gin.SecurityModule;
import stroom.statistics.impl.hbase.client.gin.StroomStatsStoreGinjector;
import stroom.statistics.impl.hbase.client.gin.StroomStatsStoreModule;
import stroom.statistics.impl.sql.client.gin.StatisticsGinjector;
import stroom.statistics.impl.sql.client.gin.StatisticsModule;
import stroom.task.client.gin.TaskGinjector;
import stroom.task.client.gin.TaskModule;
import stroom.visualisation.client.gin.VisualisationGinjector;
import stroom.visualisation.client.gin.VisualisationModule;
import stroom.welcome.client.gin.WelcomeGinjector;
import stroom.welcome.client.gin.WelcomeModule;
import stroom.widget.popup.client.gin.PopupGinjector;
import stroom.widget.popup.client.gin.PopupModule;
import stroom.xmlschema.client.gin.XMLSchemaGinjector;
import stroom.xmlschema.client.gin.XMLSchemaModule;

@GinModules({
        ActivityModule.class,
        AlertModule.class,
        AppModule.class,
        CacheModule.class,
        ClientDispatchModule.class,
        DashboardModule.class,
        DictionaryModule.class,
        EntityModule.class,
        FSVolumeModule.class,
        FeedModule.class,
        FolderModule.class,
        ImportExportConfigModule.class,
        IndexModule.class,
        KafkaConfigModule.class,
        MonitoringModule.class,
        NewUiModule.class,
        PipelineModule.class,
        PluginsModule.class,
        PolicyModule.class,
        PopupModule.class,
        QueryModule.class,
        ScriptModule.class,
        SecurityModule.class,
        SolrIndexModule.class,
        StatisticsModule.class,
        StreamStoreModule.class,
        StroomStatsStoreModule.class,
        TaskModule.class,
        VisModule.class,
        VisualisationModule.class,
        WelcomeModule.class,
        XMLSchemaModule.class
})
public interface AppGinjector extends
        AlertGinjector,
        CacheGinjector,
        DashboardGinjector,
        DictionaryGinjector,
        EntityGinjector,
        FSVolumeGinjector,
        FeedGinjector,
        FolderGinjector,
        Ginjector,
        ImportExportConfigGinjector,
        IndexGinjector,
        KafkaConfigGinjector,
        MonitoringGinjector,
        NewUiGinjector,
        PipelineGinjector,
        PluginsGinjector,
        PopupGinjector,
        ScriptGinjector,
        SecurityGinjector,
        SolrIndexGinjector,
        StatisticsGinjector,
        StreamStoreGinjector,
        StroomStatsStoreGinjector,
        TaskGinjector,
        VisGinjector,
        VisualisationGinjector,
        WelcomeGinjector,
        XMLSchemaGinjector {
    // Default implementation of standard resources
    EventBus getEventBus();

    PlaceManager getPlaceManager();

    // Presenters
    Provider<CorePresenter> getCorePresenter();

    AsyncProvider<MainPresenter> getMainPresenter();

    AsyncProvider<MenubarPresenter> getMenubarPresenter();

    AsyncProvider<ExplorerTabPanePresenter> getExplorerTabPanePresenter();

    AsyncProvider<ContentTabPanePresenter> getContentTabPanePresenter();

    AsyncProvider<ExplorerTreePresenter> getExplorerTreePresenter();

    AsyncProvider<AboutPresenter> getAboutPresenter();
}
