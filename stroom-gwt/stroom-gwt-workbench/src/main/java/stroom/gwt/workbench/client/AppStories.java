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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.client.app.activity.ActivityChooserStories;
import stroom.gwt.workbench.client.app.activity.CurrentActivityStories;
import stroom.gwt.workbench.client.app.activity.ManageActivityDialogStories;
import stroom.gwt.workbench.client.app.ai.AskAiChatPanelStories;
import stroom.gwt.workbench.client.app.ai.AskStroomAiDialogStories;
import stroom.gwt.workbench.client.app.annotations.AnnotationEditorStories;
import stroom.gwt.workbench.client.app.annotations.AnnotationTagScreenStories;
import stroom.gwt.workbench.client.app.annotations.BrowseAnnotationsScreenStories;
import stroom.gwt.workbench.client.app.annotations.CommentDecorationStories;
import stroom.gwt.workbench.client.app.contentstore.ContentStoreScreenStories;
import stroom.gwt.workbench.client.app.core.AppPermissionsStories;
import stroom.gwt.workbench.client.app.core.DeleteConfirmationStories;
import stroom.gwt.workbench.client.app.credentials.CredentialPickerDialogStories;
import stroom.gwt.workbench.client.app.credentials.CredentialsScreenStories;
import stroom.gwt.workbench.client.app.dashboard.PausePollingStories;
import stroom.gwt.workbench.client.app.dashboard.QueryInfoPromptStories;
import stroom.gwt.workbench.client.app.dashboard.QueryResultVisStories;
import stroom.gwt.workbench.client.app.dashboard.QueryResultsTableStories;
import stroom.gwt.workbench.client.app.dashboard.TableFilterSettingsStories;
import stroom.gwt.workbench.client.app.dashboard.VisSettingsStories;
import stroom.gwt.workbench.client.app.dashboard.VisToolbarStories;
import stroom.gwt.workbench.client.app.dashboard.VisTooltipStories;
import stroom.gwt.workbench.client.app.data.ContentTemplatesScreenStories;
import stroom.gwt.workbench.client.app.data.CreateProcessorsStories;
import stroom.gwt.workbench.client.app.data.DataReceiptRulesScreenStories;
import stroom.gwt.workbench.client.app.data.DataRetentionScreenStories;
import stroom.gwt.workbench.client.app.data.DataScreenStories;
import stroom.gwt.workbench.client.app.data.DataUploadDialogStories;
import stroom.gwt.workbench.client.app.data.DataVolumesScreenStories;
import stroom.gwt.workbench.client.app.data.MetaBrowserStories;
import stroom.gwt.workbench.client.app.data.SaveChangedDocumentsStories;
import stroom.gwt.workbench.client.app.dictionary.DictionaryEditorStories;
import stroom.gwt.workbench.client.app.editors.AnalyticRuleEditorStories;
import stroom.gwt.workbench.client.app.editors.CodeDocumentEditorStories;
import stroom.gwt.workbench.client.app.editors.DashboardEditorStories;
import stroom.gwt.workbench.client.app.editors.DataGenEditorStories;
import stroom.gwt.workbench.client.app.editors.DocumentationEditorStories;
import stroom.gwt.workbench.client.app.editors.ElasticClusterEditorStories;
import stroom.gwt.workbench.client.app.editors.FloorMapEditorTabStories;
import stroom.gwt.workbench.client.app.editors.FloorMapEventStoreSettingsStories;
import stroom.gwt.workbench.client.app.editors.FloorMapFormsStories;
import stroom.gwt.workbench.client.app.editors.FloorMapMapTabStories;
import stroom.gwt.workbench.client.app.editors.FloorMapSettingsStories;
import stroom.gwt.workbench.client.app.editors.FolderEditorStories;
import stroom.gwt.workbench.client.app.editors.GitRepoEditorStories;
import stroom.gwt.workbench.client.app.editors.NewFloorMapStoresStories;
import stroom.gwt.workbench.client.app.editors.OpenAiModelEditorStories;
import stroom.gwt.workbench.client.app.editors.PathwaysEditorStories;
import stroom.gwt.workbench.client.app.editors.PlanBEditorStories;
import stroom.gwt.workbench.client.app.editors.QueryEditorStories;
import stroom.gwt.workbench.client.app.editors.ReportEditorStories;
import stroom.gwt.workbench.client.app.editors.SqlTemporalStoreSettingsStories;
import stroom.gwt.workbench.client.app.editors.StatisticStoreEditorStories;
import stroom.gwt.workbench.client.app.editors.StoreEditorStories;
import stroom.gwt.workbench.client.app.editors.ViewEditorStories;
import stroom.gwt.workbench.client.app.editors.VisualisationEditorStories;
import stroom.gwt.workbench.client.app.explorer.CreateDocumentDialogStories;
import stroom.gwt.workbench.client.app.explorer.DocInfoDialogStories;
import stroom.gwt.workbench.client.app.explorer.DocumentPluginStories;
import stroom.gwt.workbench.client.app.explorer.EditTagsDialogStories;
import stroom.gwt.workbench.client.app.explorer.FindDialogStories;
import stroom.gwt.workbench.client.app.explorer.FindInContentDialogStories;
import stroom.gwt.workbench.client.app.explorer.RecentItemsDialogStories;
import stroom.gwt.workbench.client.app.explorer.RenameDocumentDialogStories;
import stroom.gwt.workbench.client.app.explorer.TabSessionChooserDialogStories;
import stroom.gwt.workbench.client.app.feed.FeedEditorStories;
import stroom.gwt.workbench.client.app.idp.AuthenticationErrorStories;
import stroom.gwt.workbench.client.app.idp.ChangePasswordDialogStories;
import stroom.gwt.workbench.client.app.idp.ChangePasswordStories;
import stroom.gwt.workbench.client.app.idp.CurrentPasswordStories;
import stroom.gwt.workbench.client.app.idp.EmailResetPasswordStories;
import stroom.gwt.workbench.client.app.idp.LoginStories;
import stroom.gwt.workbench.client.app.idp.ResetPasswordStories;
import stroom.gwt.workbench.client.app.idp.SignInFlowStories;
import stroom.gwt.workbench.client.app.importexport.DependenciesScreenStories;
import stroom.gwt.workbench.client.app.importexport.DependencyInfoDialogStories;
import stroom.gwt.workbench.client.app.importexport.ImportDialogStories;
import stroom.gwt.workbench.client.app.index.ElasticIndexEditorStories;
import stroom.gwt.workbench.client.app.index.IndexEditorStories;
import stroom.gwt.workbench.client.app.index.IndexVolumesScreenStories;
import stroom.gwt.workbench.client.app.index.SolrIndexEditorStories;
import stroom.gwt.workbench.client.app.main.AboutDialogStories;
import stroom.gwt.workbench.client.app.main.ApiErrorAlertHostStories;
import stroom.gwt.workbench.client.app.main.AppShellStories;
import stroom.gwt.workbench.client.app.main.ContentTabPaneStories;
import stroom.gwt.workbench.client.app.main.LinkPageStories;
import stroom.gwt.workbench.client.app.main.QuickFilterHistoryStories;
import stroom.gwt.workbench.client.app.main.TopBarStories;
import stroom.gwt.workbench.client.app.main.WelcomeScreenStories;
import stroom.gwt.workbench.client.app.monitoring.CachesScreenStories;
import stroom.gwt.workbench.client.app.monitoring.DatabaseTablesScreenStories;
import stroom.gwt.workbench.client.app.monitoring.PropertiesScreenStories;
import stroom.gwt.workbench.client.app.monitoring.ServerTasksScreenStories;
import stroom.gwt.workbench.client.app.monitoring.UserTaskManagerDialogStories;
import stroom.gwt.workbench.client.app.nodes.JobsScreenStories;
import stroom.gwt.workbench.client.app.nodes.NodeGroupMembersDialogStories;
import stroom.gwt.workbench.client.app.nodes.NodeGroupsScreenStories;
import stroom.gwt.workbench.client.app.nodes.NodesScreenStories;
import stroom.gwt.workbench.client.app.pathways.TracesScreenStories;
import stroom.gwt.workbench.client.app.pipeline.BeginSteppingDialogStories;
import stroom.gwt.workbench.client.app.pipeline.PipelineEditorStories;
import stroom.gwt.workbench.client.app.pipeline.PipelineTreeStories;
import stroom.gwt.workbench.client.app.pipeline.StepFilterDialogStories;
import stroom.gwt.workbench.client.app.pipeline.SteppingScreenStories;
import stroom.gwt.workbench.client.app.processing.ExecutionSchedulesScreenStories;
import stroom.gwt.workbench.client.app.processing.ProcessorFilterEditDialogStories;
import stroom.gwt.workbench.client.app.processing.ProcessorFilterListStories;
import stroom.gwt.workbench.client.app.processing.ProcessorLimitsDialogStories;
import stroom.gwt.workbench.client.app.processing.ProcessorProfilesScreenStories;
import stroom.gwt.workbench.client.app.processing.ProcessorTaskListStories;
import stroom.gwt.workbench.client.app.processing.ProcessorTaskSummaryStories;
import stroom.gwt.workbench.client.app.query.ResultStoresDialogStories;
import stroom.gwt.workbench.client.app.security.AccountsScreenStories;
import stroom.gwt.workbench.client.app.security.ApiKeysScreenStories;
import stroom.gwt.workbench.client.app.security.AppPermissionsScreenStories;
import stroom.gwt.workbench.client.app.security.DocumentCreatePermissionsGridStories;
import stroom.gwt.workbench.client.app.security.DocumentPermissionsScreenStories;
import stroom.gwt.workbench.client.app.security.DocumentPermissionsTabStories;
import stroom.gwt.workbench.client.app.security.EditAccountDialogStories;
import stroom.gwt.workbench.client.app.security.SigningKeysScreenStories;
import stroom.gwt.workbench.client.app.security.UserAccessScreenStories;
import stroom.gwt.workbench.client.app.security.UserGroupsScreenStories;
import stroom.gwt.workbench.client.app.security.UserProfileScreenStories;
import stroom.gwt.workbench.client.app.security.UserTabScreenStories;
import stroom.gwt.workbench.client.app.security.UsersScreenStories;
import stroom.gwt.workbench.client.app.vis.ChartVisStories;
import stroom.gwt.workbench.client.app.vis.MapVisStories;
import stroom.gwt.workbench.client.app.vis.NetworkVisStories;
import stroom.gwt.workbench.client.app.vis.StatusVisStories;
import stroom.gwt.workbench.client.app.vis.TimeVisStories;
import stroom.gwt.workbench.client.app.vis.VisAccessibilityStories;
import stroom.gwt.workbench.client.app.vis.VisualisationAssetsStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers the workbench's `App/*` stories.
///
/// ## Adding a story class
///
/// 1. Write the stories class in the sub-package of `stroom.gwt.workbench.client.app` for the
///    Stroom area it shows, as Stroom's client packages are grouped, e.g. `app.security` for the
///    users screen and `app.main` for the shell itself (see `widgets/buttons/ButtonStories` for an
///    example), giving it a `public static void addTo(StoryRegistry registry)` method that calls
///    `registry.component("<title>", XxxStories.class)` and `.story("<export name>", ...)` for each
///    story. The title and export name make the story's id and URL (see `WRITING-STORIES.md`).
/// 2. Call it in [#addTo] on the line after the comment holding its title, e.g.
///    ```
///    // App/Main/AboutDialog
///    XxxStories.addTo(registry);
///    ```
///    or, for a new title, add a comment for it next to the related titles. Each title has its
///    own line, so that parallel work rarely touches the same lines. Leave the comments in place.
public final class AppStories {

    private AppStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    /// @return The registry.
    public static StoryRegistry addTo(final StoryRegistry registry) {
        // App/Main/AboutDialog
        AboutDialogStories.addTo(registry);
        // App/Main/AccountsScreen
        AccountsScreenStories.addTo(registry);
        // App/Main/AnnotationEditor
        AnnotationEditorStories.addTo(registry);
        // App/Main/AnnotationTagScreen
        AnnotationTagScreenStories.addTo(registry);
        // App/Main/ApiErrorAlertHost
        ApiErrorAlertHostStories.addTo(registry);
        // App/Main/ApiKeysScreen
        ApiKeysScreenStories.addTo(registry);
        // App/Main/AppPermissionsScreen
        AppPermissionsScreenStories.addTo(registry);
        // App/Main/AppShell
        AppShellStories.addTo(registry);
        // App/Main/BeginSteppingDialog
        BeginSteppingDialogStories.addTo(registry);
        // App/Main/BrowseAnnotationsScreen
        BrowseAnnotationsScreenStories.addTo(registry);
        // App/Main/CachesScreen
        CachesScreenStories.addTo(registry);
        // App/Main/ChangePasswordDialog
        ChangePasswordDialogStories.addTo(registry);
        // App/Main/ContentStoreScreen
        ContentStoreScreenStories.addTo(registry);
        // App/Main/ContentTabPane
        ContentTabPaneStories.addTo(registry);
        // App/Main/ContentTemplatesScreen
        ContentTemplatesScreenStories.addTo(registry);
        // App/Main/CreateDocumentDialog
        CreateDocumentDialogStories.addTo(registry);
        // App/Main/CredentialPickerDialog
        CredentialPickerDialogStories.addTo(registry);
        // App/Main/CredentialsScreen
        CredentialsScreenStories.addTo(registry);
        // App/Main/DataReceiptRulesScreen
        DataReceiptRulesScreenStories.addTo(registry);
        // App/Main/DataRetentionScreen
        DataRetentionScreenStories.addTo(registry);
        // App/Main/DataScreen
        DataScreenStories.addTo(registry);
        // App/Main/DataVolumesScreen
        DataVolumesScreenStories.addTo(registry);
        // App/Main/DatabaseTablesScreen
        DatabaseTablesScreenStories.addTo(registry);
        // App/Main/DependenciesScreen
        DependenciesScreenStories.addTo(registry);
        // App/Main/DependencyInfoDialog
        DependencyInfoDialogStories.addTo(registry);
        // App/Main/DocInfoDialog
        DocInfoDialogStories.addTo(registry);
        RenameDocumentDialogStories.addTo(registry);
        // App/Main/QueryInfoDialog
        QueryInfoPromptStories.addTo(registry);
        // App/Main/DocumentCreatePermissionsGrid
        DocumentCreatePermissionsGridStories.addTo(registry);
        // App/Main/DocumentPermissionsScreen
        DocumentPermissionsScreenStories.addTo(registry);
        // App/Main/EditAccountDialog
        EditAccountDialogStories.addTo(registry);
        // App/Main/EditTagsDialog
        EditTagsDialogStories.addTo(registry);
        // App/Main/ExecutionSchedulesScreen
        ExecutionSchedulesScreenStories.addTo(registry);
        // App/Main/FindDialog
        FindDialogStories.addTo(registry);
        // App/Main/FindInContentDialog
        FindInContentDialogStories.addTo(registry);
        // App/Main/ImportDialog
        ImportDialogStories.addTo(registry);
        // App/Main/IndexVolumesScreen
        IndexVolumesScreenStories.addTo(registry);
        // App/Main/JobsScreen
        JobsScreenStories.addTo(registry);
        // App/Main/ManageActivityDialog
        ManageActivityDialogStories.addTo(registry);
        // App/Main/NodeGroupMembersDialog
        NodeGroupMembersDialogStories.addTo(registry);
        // App/Main/NodeGroupsScreen
        NodeGroupsScreenStories.addTo(registry);
        // App/Main/NodesScreen
        NodesScreenStories.addTo(registry);
        // App/Main/PipelineEditor
        PipelineEditorStories.addTo(registry);
        // App/Main/PipelineTree
        PipelineTreeStories.addTo(registry);
        // App/Main/ProcessorFilterEditDialog
        ProcessorFilterEditDialogStories.addTo(registry);
        // App/Main/ProcessorFilterList
        ProcessorFilterListStories.addTo(registry);
        // App/Main/ProcessorLimitsDialog
        ProcessorLimitsDialogStories.addTo(registry);
        // App/Main/ProcessorProfilesScreen
        ProcessorProfilesScreenStories.addTo(registry);
        // App/Main/ProcessorTaskList
        ProcessorTaskListStories.addTo(registry);
        // App/Main/ProcessorTaskSummary
        ProcessorTaskSummaryStories.addTo(registry);
        // App/Main/PropertiesScreen
        PropertiesScreenStories.addTo(registry);
        // App/Main/RecentItemsDialog
        RecentItemsDialogStories.addTo(registry);
        // App/Main/ResultStoresDialog
        ResultStoresDialogStories.addTo(registry);
        // App/Main/ServerTasksScreen
        ServerTasksScreenStories.addTo(registry);
        // App/Main/SigningKeysScreen
        SigningKeysScreenStories.addTo(registry);
        // App/Main/StepFilterDialog
        StepFilterDialogStories.addTo(registry);
        // App/Main/SteppingScreen
        SteppingScreenStories.addTo(registry);
        // App/Main/TabSessionChooserDialog
        TabSessionChooserDialogStories.addTo(registry);
        LinkPageStories.addTo(registry);
        // App/Main/TopBar
        TopBarStories.addTo(registry);
        // App/Main/TracesScreen
        TracesScreenStories.addTo(registry);
        // App/Main/UserAccessScreen
        UserAccessScreenStories.addTo(registry);
        // App/Main/UserGroupsScreen
        UserGroupsScreenStories.addTo(registry);
        // App/Main/UserProfileScreen
        UserProfileScreenStories.addTo(registry);
        // App/Main/UserTabScreen
        UserTabScreenStories.addTo(registry);
        // App/Main/UserTaskManagerDialog
        UserTaskManagerDialogStories.addTo(registry);
        // App/Main/UsersScreen
        UsersScreenStories.addTo(registry);
        QuickFilterHistoryStories.addTo(registry);
        // App/Main/VisualisationAssets
        VisualisationAssetsStories.addTo(registry);
        // App/Main/WelcomeScreen
        WelcomeScreenStories.addTo(registry);
        // App/Main/currentActivity
        CurrentActivityStories.addTo(registry);
        // App/Main/docPlugin
        DocumentPluginStories.addTo(registry);
        // App/Main/Activity Chooser
        ActivityChooserStories.addTo(registry);
        // App/Editors/AnalyticRuleEditor
        AnalyticRuleEditorStories.addTo(registry);
        // App/Editors/DashboardEditor
        DashboardEditorStories.addTo(registry);
        // App/Editors/DataGenEditor
        DataGenEditorStories.addTo(registry);
        // App/Editors/DocumentationEditor
        DocumentationEditorStories.addTo(registry);
        // App/Editors/ElasticClusterEditor
        ElasticClusterEditorStories.addTo(registry);
        // App/Editors/FloorMapEditor
        FloorMapEditorTabStories.addTo(registry);
        FloorMapSettingsStories.addTo(registry);
        // App/Editors/FloorMapForms
        FloorMapFormsStories.addTo(registry);
        // App/Editors/FloorMapInit
        NewFloorMapStoresStories.addTo(registry);
        // App/Editors/FloorMapMap
        FloorMapMapTabStories.addTo(registry);
        // App/Editors/FolderEditor
        FolderEditorStories.addTo(registry);
        // App/Editors/GitRepoEditor
        GitRepoEditorStories.addTo(registry);
        // App/Editors/OpenAiModelEditor
        OpenAiModelEditorStories.addTo(registry);
        // App/Editors/PathwaysEditor
        PathwaysEditorStories.addTo(registry);
        // App/Editors/PlanBEditor
        PlanBEditorStories.addTo(registry);
        // App/Editors/QueryEditor
        QueryEditorStories.addTo(registry);
        // App/Editors/ReportEditor
        ReportEditorStories.addTo(registry);
        // App/Editors/StatisticStoreEditor
        StatisticStoreEditorStories.addTo(registry);
        // App/Editors/ViewEditor
        ViewEditorStories.addTo(registry);
        // App/Editors/VisualisationEditor
        VisualisationEditorStories.addTo(registry);
        // App/Editors/CodeDocumentEditor
        CodeDocumentEditorStories.addTo(registry);
        // App/AI/AskAiChatPanel
        AskAiChatPanelStories.addTo(registry);
        // App/AI/AskStroomAiDialog
        AskStroomAiDialogStories.addTo(registry);
        // App/Dashboard/TableFilterSettings
        TableFilterSettingsStories.addTo(registry);
        // App/Dashboard/QueryResultVis
        QueryResultVisStories.addTo(registry);
        // App/Dashboard/VisTooltip
        VisTooltipStories.addTo(registry);
        // App/Dashboard/VisToolbar
        VisToolbarStories.addTo(registry);
        VisSettingsStories.addTo(registry);
        // App/Visualisations/BarChart, LineChart, StackedArea, TimeSeries, Scatter, Bubble, Doughnut,
        // TreeMap, WordCloud
        ChartVisStories.addTo(registry);
        // App/Visualisations/DayWeekHeatMap, HourDayMultiHeatMap, HourDayPointMap, HourDaySessionMap,
        // Session, SeriesSessionMap
        TimeVisStories.addTo(registry);
        // App/Visualisations/Gauge, RAGStatus, TrafficLights, TextValue
        StatusVisStories.addTo(registry);
        // App/Visualisations/Force, ChordLabels, Sunburst, Tree
        NetworkVisStories.addTo(registry);
        // App/Visualisations/GeoMap, FloorMap
        MapVisStories.addTo(registry);
        // App/Visualisations/Accessibility
        VisAccessibilityStories.addTo(registry);
        // App/Dashboard/PausePolling
        PausePollingStories.addTo(registry);
        // App/Data/DataUploadDialog
        DataUploadDialogStories.addTo(registry);
        // App/Data/MetaBrowser
        MetaBrowserStories.addTo(registry);
        // App/Data/DocRefSelectionDialog
        SaveChangedDocumentsStories.addTo(registry);
        // App/Data/ProcessChoiceDialog
        CreateProcessorsStories.addTo(registry);
        // App/Dictionary/DictionaryEditor
        DictionaryEditorStories.addTo(registry);
        // App/Security/DocumentPermissionsTab
        DocumentPermissionsTabStories.addTo(registry);
        // App/Index/ElasticIndexEditor
        ElasticIndexEditorStories.addTo(registry);
        // App/Index/IndexEditor
        IndexEditorStories.addTo(registry);
        // App/Editors/SqlTemporalStoreSettings
        SqlTemporalStoreSettingsStories.addTo(registry);
        StoreEditorStories.addTo(registry);
        // App/Editors/FloorMapEventStoreSettings
        FloorMapEventStoreSettingsStories.addTo(registry);
        // App/Index/SolrIndexEditor
        SolrIndexEditorStories.addTo(registry);
        // App/Feed/FeedEditor
        FeedEditorStories.addTo(registry);
        // App/Query/QueryResultsTable
        QueryResultsTableStories.addTo(registry);
        // App/Core/appPermissions
        AppPermissionsStories.addTo(registry);
        // App/Core/deleteConfirmation
        DeleteConfirmationStories.addTo(registry);
        // App/Annotations/decorateComment
        CommentDecorationStories.addTo(registry);
        // App/IdP/AuthenticationError
        AuthenticationErrorStories.addTo(registry);
        // App/IdP/ChangePassword
        ChangePasswordStories.addTo(registry);
        // App/IdP/CurrentPassword
        CurrentPasswordStories.addTo(registry);
        // App/IdP/EmailResetPassword
        EmailResetPasswordStories.addTo(registry);
        // App/IdP/ResetPassword
        ResetPasswordStories.addTo(registry);
        // App/IdP/Login
        LoginStories.addTo(registry);
        // App/IdP/SignInFlow
        SignInFlowStories.addTo(registry);
        return registry;
    }
}
