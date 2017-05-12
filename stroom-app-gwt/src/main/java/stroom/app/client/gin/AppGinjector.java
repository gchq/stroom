/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import stroom.about.client.presenter.AboutPresenter;
import stroom.alert.client.gin.AlertGinjector;
import stroom.alert.client.gin.AlertModule;
import stroom.app.client.presenter.AppPresenter;
import stroom.cache.client.gin.CacheGinjector;
import stroom.cache.client.gin.CacheModule;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.dashboard.client.gin.DashboardGinjector;
import stroom.dashboard.client.gin.DashboardModule;
import stroom.dashboard.client.vis.gin.VisGinjector;
import stroom.dashboard.client.vis.gin.VisModule;
import stroom.dictionary.client.gin.DictionaryGinjector;
import stroom.dictionary.client.gin.DictionaryModule;
import stroom.dispatch.client.ClientDispatchModule;
import stroom.entity.client.gin.EntityGinjector;
import stroom.entity.client.gin.EntityModule;
import stroom.explorer.client.presenter.ExplorerTabPanePresenter;
import stroom.explorer.client.presenter.ExplorerTreePresenter;
import stroom.feed.client.gin.FeedGinjector;
import stroom.feed.client.gin.FeedModule;
import stroom.folder.client.FolderRootPresenter;
import stroom.folder.client.gin.FolderGinjector;
import stroom.folder.client.gin.FolderModule;
import stroom.importexport.client.gin.ImportExportConfigGinjector;
import stroom.importexport.client.gin.ImportExportConfigModule;
import stroom.index.client.gin.IndexGinjector;
import stroom.index.client.gin.IndexModule;
import stroom.main.client.presenter.MainPresenter;
import stroom.menubar.client.presenter.MenubarPresenter;
import stroom.monitoring.client.gin.MonitoringGinjector;
import stroom.monitoring.client.gin.MonitoringModule;
import stroom.pipeline.client.gin.PipelineGinjector;
import stroom.pipeline.client.gin.PipelineModule;
import stroom.query.client.QueryModule;
import stroom.script.client.gin.ScriptGinjector;
import stroom.script.client.gin.ScriptModule;
import stroom.security.client.gin.SecurityGinjector;
import stroom.security.client.gin.SecurityModule;
import stroom.statistics.client.common.gin.StatisticsGinjector;
import stroom.statistics.client.common.gin.StatisticsModule;
import stroom.stats.client.gin.StroomStatsStoreGinjector;
import stroom.stats.client.gin.StroomStatsStoreModule;
import stroom.streamstore.client.gin.StreamStoreGinjector;
import stroom.streamstore.client.gin.StreamStoreModule;
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
        AlertModule.class,
        AppModule.class,
        CacheModule.class,
        ClientDispatchModule.class,
        DashboardModule.class,
        DictionaryModule.class,
        EntityModule.class,
        FeedModule.class,
        FolderModule.class,
        ImportExportConfigModule.class,
        IndexModule.class,
        MonitoringModule.class,
        PipelineModule.class,
        PluginsModule.class,
        PopupModule.class,
        QueryModule.class,
        ScriptModule.class,
        SecurityModule.class,
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
        FeedGinjector,
        FolderGinjector,
        Ginjector,
        ImportExportConfigGinjector,
        IndexGinjector,
        MonitoringGinjector,
        PipelineGinjector,
        PluginsGinjector,
        PopupGinjector,
        ScriptGinjector,
        SecurityGinjector,
        StatisticsGinjector,
        StreamStoreGinjector,
        StroomStatsStoreGinjector,
        TaskGinjector,
        VisGinjector,
        VisualisationGinjector,
        WelcomeGinjector,
        XMLSchemaGinjector
{
    // Default implementation of standard resources
    EventBus getEventBus();

    PlaceManager getPlaceManager();

    // Presenters
    Provider<AppPresenter> getAppPresenter();

    AsyncProvider<MainPresenter> getMainPresenter();

    AsyncProvider<MenubarPresenter> getMenubarPresenter();

    AsyncProvider<ExplorerTabPanePresenter> getExplorerTabPanePresenter();

    AsyncProvider<ContentTabPanePresenter> getContentTabPanePresenter();

    AsyncProvider<ExplorerTreePresenter> getExplorerTreePresenter();

    AsyncProvider<AboutPresenter> getAboutPresenter();

    AsyncProvider<FolderRootPresenter> getFolderRootPresenter();
}
