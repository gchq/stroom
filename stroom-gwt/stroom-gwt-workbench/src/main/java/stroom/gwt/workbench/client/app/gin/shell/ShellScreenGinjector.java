/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.shell;

import stroom.about.client.AboutPlugin;
import stroom.activity.client.CurrentActivity;
import stroom.activity.client.SplashPresenter;
import stroom.annotation.client.AnnotationBrowsePlugin;
import stroom.annotation.client.AnnotationCollectionPlugin;
import stroom.annotation.client.AnnotationCommentPlugin;
import stroom.annotation.client.AnnotationCreatePlugin;
import stroom.annotation.client.AnnotationLabelPlugin;
import stroom.annotation.client.AnnotationStatusPlugin;
import stroom.cache.client.CacheMonitoringPlugin;
import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.contentstore.client.ContentStorePlugin;
import stroom.core.client.ContentManager;
import stroom.credentials.client.CredentialsPlugin;
import stroom.data.store.impl.fs.client.ManageFsVolumesPlugin;
import stroom.dictionary.client.DictionaryPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.DocumentPluginRegistry;
import stroom.entity.client.presenter.CopyDocumentPresenter;
import stroom.entity.client.presenter.CreateDocumentPresenter;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.entity.client.presenter.MoveDocumentPresenter;
import stroom.entity.client.presenter.NameDocumentPresenter;
import stroom.explorer.client.NavigationPlugin;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter;
import stroom.explorer.client.presenter.FindInContentPresenter;
import stroom.explorer.client.presenter.FindPresenter;
import stroom.explorer.client.presenter.NavigationPresenter;
import stroom.explorer.client.presenter.RecentItemsPresenter;
import stroom.explorer.client.presenter.TabSessionManager;
import stroom.feed.client.FeedPlugin;
import stroom.folder.client.FolderFavouritesPlugin;
import stroom.folder.client.FolderPlugin;
import stroom.folder.client.FolderRootPlugin;
import stroom.gwt.workbench.client.app.gin.CredentialsScreenModule;
import stroom.gwt.workbench.client.app.gin.DictionaryScreenModule;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ExplorerScreenModule;
import stroom.gwt.workbench.client.app.gin.MonitoringScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.SecurityScreenModule;
import stroom.gwt.workbench.client.app.gin.TaskScreenModule;
import stroom.gwt.workbench.client.app.gin.content.ActivityScreenModule;
import stroom.gwt.workbench.client.app.gin.content.AnnotationScreenModule;
import stroom.gwt.workbench.client.app.gin.content.CacheScreenModule;
import stroom.gwt.workbench.client.app.gin.content.ContentAppScreenModule;
import stroom.gwt.workbench.client.app.gin.content.ContentExtrasScreenModule;
import stroom.gwt.workbench.client.app.gin.content.ContentStoreScreenModule;
import stroom.gwt.workbench.client.app.gin.content.ContentTemplateScreenModule;
import stroom.gwt.workbench.client.app.gin.content.FsVolumeScreenModule;
import stroom.gwt.workbench.client.app.gin.content.ImportExportScreenModule;
import stroom.gwt.workbench.client.app.gin.content.IndexVolumeScreenModule;
import stroom.gwt.workbench.client.app.gin.content.PathwaysScreenModule;
import stroom.gwt.workbench.client.app.gin.content.PipelineScreenModule;
import stroom.gwt.workbench.client.app.gin.content.PlanBScreenModule;
import stroom.gwt.workbench.client.app.gin.content.ResultStoreScreenModule;
import stroom.gwt.workbench.client.app.gin.content.StreamStoreScreenModule;
import stroom.gwt.workbench.client.app.gin.editors.FolderScreenModule;
import stroom.gwt.workbench.client.app.gin.processing.AnalyticsScreenModule;
import stroom.gwt.workbench.client.app.gin.processing.PolicyScreenModule;
import stroom.gwt.workbench.client.app.gin.security.IdentityScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.help.client.HelpPlugin;
import stroom.importexport.client.DependenciesPlugin;
import stroom.importexport.client.ExportConfigPlugin;
import stroom.importexport.client.ImportConfigPlugin;
import stroom.importexport.client.presenter.ExportConfigPresenter;
import stroom.index.client.ManageIndexVolumesPlugin;
import stroom.main.client.presenter.GlobalKeyHandlerImpl;
import stroom.main.client.presenter.MainPresenter.MainView;
import stroom.monitoring.client.DatabaseTablesMonitoringPlugin;
import stroom.monitoring.client.ExecutionScheduleManagerPlugin;
import stroom.monitoring.client.JobListPlugin;
import stroom.monitoring.client.NodeGroupsPlugin;
import stroom.monitoring.client.NodeMonitoringPlugin;
import stroom.monitoring.client.ProcessorProfilePlugin;
import stroom.node.client.ManageGlobalPropertiesPlugin;
import stroom.pathways.client.TracesPlugin;
import stroom.preferences.client.UserPreferencesPlugin;
import stroom.query.client.ResultStorePlugin;
import stroom.receive.rules.client.ContentTemplatePlugin;
import stroom.receive.rules.client.DataRetentionPlugin;
import stroom.receive.rules.client.ReceiveDataRuleSetPlugin;
import stroom.security.client.ApiKeysPlugin;
import stroom.security.client.AppPermissionsPlugin;
import stroom.security.client.DocumentPermissionsPlugin;
import stroom.security.client.LogoutPlugin;
import stroom.security.client.SignOutOtherSessionsPlugin;
import stroom.security.client.SigningKeyPlugin;
import stroom.security.client.UserAccessPlugin;
import stroom.security.client.UserPermissionsReportPlugin;
import stroom.security.client.UserPlugin;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.UsersPlugin;
import stroom.security.identity.client.AccountsPlugin;
import stroom.security.identity.client.ChangePasswordPlugin;
import stroom.task.client.TaskManagerPlugin;

import com.google.gwt.inject.client.GinModules;

/// The `shell` batch's [ScreenGinjector]: it creates Stroom's app shell for the `App/Main/AppShell`
/// stories (the main view, the explorer, the content tab pane, the app's plugins and the dialogs
/// they open) as Stroom's GIN modules (`AppGinjectorUser`) do. Create one each time the story
/// renders and give it to the harness with `ScreenHarness.Builder.injector(...)`.
///
/// Its modules are the shared mirrors in `app.gin`, other batches' mirrors (used as they are) and
/// [ShellAppScreenModule]. Stroom binds its plugins as eager singletons; here a plugin is created
/// (and registers itself on the event bus) when the story gets it from the ginjector, once the
/// harness has been built.
@GinModules({
        ScreenViewsModule.class,
        CredentialsScreenModule.class,
        DictionaryScreenModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        MonitoringScreenModule.class,
        SecurityScreenModule.class,
        TaskScreenModule.class,
        PipelineScreenModule.class,
        StreamStoreScreenModule.class,
        CacheScreenModule.class,
        ImportExportScreenModule.class,
        AnnotationScreenModule.class,
        ContentAppScreenModule.class,
        ActivityScreenModule.class,
        ContentExtrasScreenModule.class,
        ResultStoreScreenModule.class,
        ContentStoreScreenModule.class,
        IndexVolumeScreenModule.class,
        FsVolumeScreenModule.class,
        PathwaysScreenModule.class,
        PlanBScreenModule.class,
        ContentTemplateScreenModule.class,
        FolderScreenModule.class,
        AnalyticsScreenModule.class,
        PolicyScreenModule.class,
        IdentityScreenModule.class,
        ShellAppScreenModule.class,
})
public interface ShellScreenGinjector extends ScreenGinjector {

    // The shell

    /// @return The explorer (Stroom's navigation panel), shown in the main view's explorer slot.
    NavigationPresenter getNavigationPresenter();

    /// @return The main view (the menu bar and the explorer and content split), for the story's `MainPresenter`.
    MainView getMainView();

    /// @return Stroom's app-wide key handler (keyboard shortcuts), which the story gives its `MainPresenter`.
    GlobalKeyHandlerImpl getGlobalKeyHandlerImpl();

    /// @return The document tab pane, shown in the main view's content slot.
    ContentTabPanePresenter getContentTabPanePresenter();

    /// @return The content manager (a singleton), which opens tabs in the tab pane.
    ContentManager getContentManager();

    /// @return The document plugin event manager (a singleton): the explorer's and tabs' menus, and
    /// opening, saving, renaming, copying, moving and deleting documents.
    DocumentPluginEventManager getDocumentPluginEventManager();

    /// @return The document plugin registry (a singleton), which the document plugins register with.
    DocumentPluginRegistry getDocumentPluginRegistry();

    /// @return The tab session manager (Stroom binds it as a plugin).
    TabSessionManager getTabSessionManager();

    /// @return The splash screen (terms of use) Stroom shows at login.
    SplashPresenter getSplashPresenter();

    /// @return The current activity (a singleton), which shows the activity chooser.
    CurrentActivity getCurrentActivity();

    /// @return The 'Find' dialog, shown for `ShowFindEvent`.
    FindPresenter getFindPresenter();

    /// @return The 'Find In Content' dialog, shown for `ShowFindInContentEvent`.
    FindInContentPresenter getFindInContentPresenter();

    /// @return The 'Recent Items' dialog, shown for `ShowRecentItemsEvent`.
    RecentItemsPresenter getRecentItemsPresenter();

    /// @return The 'New ...' and 'Save As' dialog, shown for `ShowCreateDocumentDialogEvent`.
    CreateDocumentPresenter getCreateDocumentPresenter();

    /// @return The 'Copy' dialog, shown for `ShowCopyDocumentDialogEvent`.
    CopyDocumentPresenter getCopyDocumentPresenter();

    /// @return The 'Move' dialog, shown for `ShowMoveDocumentDialogEvent`.
    MoveDocumentPresenter getMoveDocumentPresenter();

    /// @return The 'Rename' dialog, shown for `ShowRenameDocumentDialogEvent`.
    NameDocumentPresenter getNameDocumentPresenter();

    /// @return The document 'Info' dialog, shown for `ShowInfoDocumentDialogEvent`.
    InfoDocumentPresenter getInfoDocumentPresenter();

    /// @return The 'Export' dialog, shown for `ExportConfigEvent`.
    ExportConfigPresenter getExportConfigPresenter();

    /// @return The 'Edit Tags' dialog, shown for `ShowEditNodeTagsDialogEvent`.
    ExplorerNodeEditTagsPresenter getExplorerNodeEditTagsPresenter();

    /// @return The 'Remove Tags' dialog, shown for `ShowRemoveNodeTagsDialogEvent`.
    ExplorerNodeRemoveTagsPresenter getExplorerNodeRemoveTagsPresenter();

    // Plugins adding to the main menu (and their keyboard shortcuts)

    /// @return Stroom's `AboutPlugin`, registered as it is created.
    AboutPlugin getAboutPlugin();

    /// @return Stroom's `AnnotationBrowsePlugin`, registered as it is created.
    AnnotationBrowsePlugin getAnnotationBrowsePlugin();

    /// @return Stroom's `AnnotationCreatePlugin`, registered as it is created.
    AnnotationCreatePlugin getAnnotationCreatePlugin();

    /// @return Stroom's `AnnotationCollectionPlugin`, registered as it is created.
    AnnotationCollectionPlugin getAnnotationCollectionPlugin();

    /// @return Stroom's `AnnotationCommentPlugin`, registered as it is created.
    AnnotationCommentPlugin getAnnotationCommentPlugin();

    /// @return Stroom's `AnnotationLabelPlugin`, registered as it is created.
    AnnotationLabelPlugin getAnnotationLabelPlugin();

    /// @return Stroom's `AnnotationStatusPlugin`, registered as it is created.
    AnnotationStatusPlugin getAnnotationStatusPlugin();

    /// @return Stroom's `CacheMonitoringPlugin`, registered as it is created.
    CacheMonitoringPlugin getCacheMonitoringPlugin();

    /// @return Stroom's `ContentStorePlugin`, registered as it is created.
    ContentStorePlugin getContentStorePlugin();

    /// @return Stroom's `CredentialsPlugin`, registered as it is created.
    CredentialsPlugin getCredentialsPlugin();

    /// @return Stroom's `ManageFsVolumesPlugin`, registered as it is created.
    ManageFsVolumesPlugin getManageFsVolumesPlugin();

    /// @return Stroom's `NavigationPlugin`, registered as it is created.
    NavigationPlugin getNavigationPlugin();

    /// @return Stroom's `HelpPlugin`, registered as it is created.
    HelpPlugin getHelpPlugin();

    /// @return Stroom's `DependenciesPlugin`, registered as it is created.
    DependenciesPlugin getDependenciesPlugin();

    /// @return Stroom's `ExportConfigPlugin`, registered as it is created.
    ExportConfigPlugin getExportConfigPlugin();

    /// @return Stroom's `ImportConfigPlugin`, registered as it is created.
    ImportConfigPlugin getImportConfigPlugin();

    /// @return Stroom's `ManageIndexVolumesPlugin`, registered as it is created.
    ManageIndexVolumesPlugin getManageIndexVolumesPlugin();

    /// @return Stroom's `DatabaseTablesMonitoringPlugin`, registered as it is created.
    DatabaseTablesMonitoringPlugin getDatabaseTablesMonitoringPlugin();

    /// @return Stroom's `ExecutionScheduleManagerPlugin`, registered as it is created.
    ExecutionScheduleManagerPlugin getExecutionScheduleManagerPlugin();

    /// @return Stroom's `JobListPlugin`, registered as it is created.
    JobListPlugin getJobListPlugin();

    /// @return Stroom's `NodeGroupsPlugin`, registered as it is created.
    NodeGroupsPlugin getNodeGroupsPlugin();

    /// @return Stroom's `NodeMonitoringPlugin`, registered as it is created.
    NodeMonitoringPlugin getNodeMonitoringPlugin();

    /// @return Stroom's `ProcessorProfilePlugin`, registered as it is created.
    ProcessorProfilePlugin getProcessorProfilePlugin();

    /// @return Stroom's `ManageGlobalPropertiesPlugin`, registered as it is created.
    ManageGlobalPropertiesPlugin getManageGlobalPropertiesPlugin();

    /// @return Stroom's `TracesPlugin`, registered as it is created.
    TracesPlugin getTracesPlugin();

    /// @return Stroom's `UserPreferencesPlugin`, registered as it is created.
    UserPreferencesPlugin getUserPreferencesPlugin();

    /// @return Stroom's `ResultStorePlugin`, registered as it is created.
    ResultStorePlugin getResultStorePlugin();

    /// @return Stroom's `ContentTemplatePlugin`, registered as it is created.
    ContentTemplatePlugin getContentTemplatePlugin();

    /// @return Stroom's `DataRetentionPlugin`, registered as it is created.
    DataRetentionPlugin getDataRetentionPlugin();

    /// @return Stroom's `ReceiveDataRuleSetPlugin`, registered as it is created.
    ReceiveDataRuleSetPlugin getReceiveDataRuleSetPlugin();

    /// @return Stroom's `ApiKeysPlugin`, registered as it is created.
    ApiKeysPlugin getApiKeysPlugin();

    /// @return Stroom's `AppPermissionsPlugin`, registered as it is created.
    AppPermissionsPlugin getAppPermissionsPlugin();

    /// @return Stroom's `DocumentPermissionsPlugin`, registered as it is created.
    DocumentPermissionsPlugin getDocumentPermissionsPlugin();

    /// @return Stroom's `LogoutPlugin`, registered as it is created.
    LogoutPlugin getLogoutPlugin();

    /// @return Stroom's `SigningKeyPlugin`, registered as it is created.
    SigningKeyPlugin getSigningKeyPlugin();

    /// @return Stroom's `SignOutOtherSessionsPlugin`, registered as it is created.
    SignOutOtherSessionsPlugin getSignOutOtherSessionsPlugin();

    /// @return Stroom's `UserAccessPlugin`, registered as it is created.
    UserAccessPlugin getUserAccessPlugin();

    /// @return Stroom's `UserPermissionsReportPlugin`, registered as it is created.
    UserPermissionsReportPlugin getUserPermissionsReportPlugin();

    /// @return Stroom's `UserPlugin`, registered as it is created.
    UserPlugin getUserPlugin();

    /// @return Stroom's `UsersAndGroupsPlugin`, registered as it is created.
    UsersAndGroupsPlugin getUsersAndGroupsPlugin();

    /// @return Stroom's `UsersPlugin`, registered as it is created.
    UsersPlugin getUsersPlugin();

    /// @return Stroom's `AccountsPlugin`, registered as it is created.
    AccountsPlugin getAccountsPlugin();

    /// @return Stroom's `ChangePasswordPlugin`, registered as it is created.
    ChangePasswordPlugin getChangePasswordPlugin();

    /// @return Stroom's `TaskManagerPlugin`, registered as it is created.
    TaskManagerPlugin getTaskManagerPlugin();

    // Document plugins (opening and saving documents of their type)

    /// @return Stroom's `DictionaryPlugin`, registered as it is created.
    DictionaryPlugin getDictionaryPlugin();

    /// @return Stroom's `FeedPlugin`, registered as it is created.
    FeedPlugin getFeedPlugin();

    /// @return Stroom's `FolderPlugin`, registered as it is created.
    FolderPlugin getFolderPlugin();

    /// @return Stroom's `FolderFavouritesPlugin`, registered as it is created.
    FolderFavouritesPlugin getFolderFavouritesPlugin();

    /// @return Stroom's `FolderRootPlugin`, registered as it is created.
    FolderRootPlugin getFolderRootPlugin();
}
