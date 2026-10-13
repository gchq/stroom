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

import stroom.gwt.workbench.client.app.ai.AskAiChatPanelStories;
import stroom.gwt.workbench.client.app.ai.AskStroomAiDialogStories;
import stroom.gwt.workbench.client.app.annotations.DecorateCommentStories;
import stroom.gwt.workbench.client.app.core.AppPermissionsStories;
import stroom.gwt.workbench.client.app.core.DeleteConfirmationStories;
import stroom.gwt.workbench.client.app.dashboard.PausePollingStories;
import stroom.gwt.workbench.client.app.dashboard.QueryResultVisStories;
import stroom.gwt.workbench.client.app.dashboard.QueryResultsTableDashboardStories;
import stroom.gwt.workbench.client.app.dashboard.TableFilterSettingsStories;
import stroom.gwt.workbench.client.app.data.DataUploadDialogStories;
import stroom.gwt.workbench.client.app.data.MetaBrowserStories;
import stroom.gwt.workbench.client.app.data.ProcessChoiceDialogStories;
import stroom.gwt.workbench.client.app.dictionary.DictionaryEditorStories;
import stroom.gwt.workbench.client.app.editors.AnalyticRuleEditorStories;
import stroom.gwt.workbench.client.app.editors.CodeDocumentEditorStories;
import stroom.gwt.workbench.client.app.editors.DashboardEditorStories;
import stroom.gwt.workbench.client.app.editors.DataGenEditorStories;
import stroom.gwt.workbench.client.app.editors.DocumentationEditorStories;
import stroom.gwt.workbench.client.app.editors.ElasticClusterEditorStories;
import stroom.gwt.workbench.client.app.editors.FolderEditorStories;
import stroom.gwt.workbench.client.app.editors.GitRepoEditorStories;
import stroom.gwt.workbench.client.app.editors.OpenAiModelEditorStories;
import stroom.gwt.workbench.client.app.editors.PathwaysEditorStories;
import stroom.gwt.workbench.client.app.editors.PlanBEditorStories;
import stroom.gwt.workbench.client.app.editors.QueryEditorStories;
import stroom.gwt.workbench.client.app.editors.ReportEditorStories;
import stroom.gwt.workbench.client.app.editors.StatisticStoreEditorStories;
import stroom.gwt.workbench.client.app.editors.ViewEditorStories;
import stroom.gwt.workbench.client.app.editors.VisualisationEditorStories;
import stroom.gwt.workbench.client.app.feed.FeedEditorStories;
import stroom.gwt.workbench.client.app.idp.AuthenticationErrorStories;
import stroom.gwt.workbench.client.app.idp.ChangePasswordStories;
import stroom.gwt.workbench.client.app.idp.CurrentPasswordStories;
import stroom.gwt.workbench.client.app.idp.EmailResetPasswordStories;
import stroom.gwt.workbench.client.app.idp.LoginStories;
import stroom.gwt.workbench.client.app.idp.ResetPasswordStories;
import stroom.gwt.workbench.client.app.idp.SignInFlowStories;
import stroom.gwt.workbench.client.app.index.ElasticIndexEditorStories;
import stroom.gwt.workbench.client.app.index.IndexEditorStories;
import stroom.gwt.workbench.client.app.index.SolrIndexEditorStories;
import stroom.gwt.workbench.client.app.main.AboutDialogStories;
import stroom.gwt.workbench.client.app.main.AccountsScreenStories;
import stroom.gwt.workbench.client.app.main.ActivityChooserStories;
import stroom.gwt.workbench.client.app.main.AnnotationEditorStories;
import stroom.gwt.workbench.client.app.main.AnnotationTagScreenStories;
import stroom.gwt.workbench.client.app.main.ApiErrorAlertHostStories;
import stroom.gwt.workbench.client.app.main.ApiKeysScreenStories;
import stroom.gwt.workbench.client.app.main.AppPermissionsScreenStories;
import stroom.gwt.workbench.client.app.main.AppShellStories;
import stroom.gwt.workbench.client.app.main.BeginSteppingDialogStories;
import stroom.gwt.workbench.client.app.main.BrowseAnnotationsScreenStories;
import stroom.gwt.workbench.client.app.main.CachesScreenStories;
import stroom.gwt.workbench.client.app.main.ChangePasswordDialogStories;
import stroom.gwt.workbench.client.app.main.ContentStoreScreenStories;
import stroom.gwt.workbench.client.app.main.ContentTabPaneStories;
import stroom.gwt.workbench.client.app.main.ContentTemplatesScreenStories;
import stroom.gwt.workbench.client.app.main.CreateDocumentDialogStories;
import stroom.gwt.workbench.client.app.main.CredentialPickerDialogStories;
import stroom.gwt.workbench.client.app.main.CredentialsScreenStories;
import stroom.gwt.workbench.client.app.main.CurrentActivityStories;
import stroom.gwt.workbench.client.app.main.DataReceiptRulesScreenStories;
import stroom.gwt.workbench.client.app.main.DataRetentionScreenStories;
import stroom.gwt.workbench.client.app.main.DataScreenStories;
import stroom.gwt.workbench.client.app.main.DataVolumesScreenStories;
import stroom.gwt.workbench.client.app.main.DatabaseTablesScreenStories;
import stroom.gwt.workbench.client.app.main.DependenciesScreenStories;
import stroom.gwt.workbench.client.app.main.DependencyInfoDialogStories;
import stroom.gwt.workbench.client.app.main.DocInfoDialogStories;
import stroom.gwt.workbench.client.app.main.DocPluginStories;
import stroom.gwt.workbench.client.app.main.DocumentCreatePermissionsGridStories;
import stroom.gwt.workbench.client.app.main.DocumentPermissionsScreenStories;
import stroom.gwt.workbench.client.app.main.EditAccountDialogStories;
import stroom.gwt.workbench.client.app.main.EditTagsDialogStories;
import stroom.gwt.workbench.client.app.main.ExecutionSchedulesScreenStories;
import stroom.gwt.workbench.client.app.main.FindDialogStories;
import stroom.gwt.workbench.client.app.main.FindInContentDialogStories;
import stroom.gwt.workbench.client.app.main.ImportDialogStories;
import stroom.gwt.workbench.client.app.main.IndexVolumesScreenStories;
import stroom.gwt.workbench.client.app.main.JobsScreenStories;
import stroom.gwt.workbench.client.app.main.ManageActivityDialogStories;
import stroom.gwt.workbench.client.app.main.NodeGroupMembersDialogStories;
import stroom.gwt.workbench.client.app.main.NodeGroupsScreenStories;
import stroom.gwt.workbench.client.app.main.NodesScreenStories;
import stroom.gwt.workbench.client.app.main.PipelineEditorStories;
import stroom.gwt.workbench.client.app.main.PipelineTreeStories;
import stroom.gwt.workbench.client.app.main.ProcessorFilterEditDialogStories;
import stroom.gwt.workbench.client.app.main.ProcessorFilterListStories;
import stroom.gwt.workbench.client.app.main.ProcessorLimitsDialogStories;
import stroom.gwt.workbench.client.app.main.ProcessorProfilesScreenStories;
import stroom.gwt.workbench.client.app.main.PropertiesScreenStories;
import stroom.gwt.workbench.client.app.main.RecentItemsDialogStories;
import stroom.gwt.workbench.client.app.main.ResultStoresDialogStories;
import stroom.gwt.workbench.client.app.main.ServerTasksScreenStories;
import stroom.gwt.workbench.client.app.main.SigningKeysScreenStories;
import stroom.gwt.workbench.client.app.main.StepFilterDialogStories;
import stroom.gwt.workbench.client.app.main.SteppingScreenStories;
import stroom.gwt.workbench.client.app.main.TabSessionChooserDialogStories;
import stroom.gwt.workbench.client.app.main.TracesScreenStories;
import stroom.gwt.workbench.client.app.main.UserAccessScreenStories;
import stroom.gwt.workbench.client.app.main.UserGroupsScreenStories;
import stroom.gwt.workbench.client.app.main.UserProfileScreenStories;
import stroom.gwt.workbench.client.app.main.UserTabScreenStories;
import stroom.gwt.workbench.client.app.main.UserTaskManagerDialogStories;
import stroom.gwt.workbench.client.app.main.UsersScreenStories;
import stroom.gwt.workbench.client.app.main.VisualisationAssetsStories;
import stroom.gwt.workbench.client.app.main.WelcomeScreenStories;
import stroom.gwt.workbench.client.app.query.QueryResultsTableStories;
import stroom.gwt.workbench.client.app.security.DocumentPermissionsTabStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers the workbench's `App/*` stories.
///
/// ## Adding a story class
///
/// 1. Write the stories class in a sub-package of `stroom.gwt.workbench.client.app` for its area,
///    e.g. `app.main` for `App/Main/*` (see `widgets/buttons/ButtonStories` for an example), giving
///    it a `public static void addTo(StoryRegistry registry)` method that calls
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
        // App/Main/VisualisationAssets
        VisualisationAssetsStories.addTo(registry);
        // App/Main/WelcomeScreen
        WelcomeScreenStories.addTo(registry);
        // App/Main/currentActivity
        CurrentActivityStories.addTo(registry);
        // App/Main/docPlugin
        DocPluginStories.addTo(registry);
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
        // App/Dashboard/PausePolling
        PausePollingStories.addTo(registry);
        // App/Data/DataUploadDialog
        DataUploadDialogStories.addTo(registry);
        // App/Data/MetaBrowser
        MetaBrowserStories.addTo(registry);
        // App/Data/ProcessChoiceDialog
        ProcessChoiceDialogStories.addTo(registry);
        // App/Dictionary/DictionaryEditor
        DictionaryEditorStories.addTo(registry);
        // App/Security/DocumentPermissionsTab
        DocumentPermissionsTabStories.addTo(registry);
        // App/Index/ElasticIndexEditor
        ElasticIndexEditorStories.addTo(registry);
        // App/Index/IndexEditor
        IndexEditorStories.addTo(registry);
        // App/Index/SolrIndexEditor
        SolrIndexEditorStories.addTo(registry);
        // App/Feed/FeedEditor
        FeedEditorStories.addTo(registry);
        // App/Query/QueryResultsTable
        QueryResultsTableStories.addTo(registry);
        QueryResultsTableDashboardStories.addTo(registry);
        // App/Core/appPermissions
        AppPermissionsStories.addTo(registry);
        // App/Core/deleteConfirmation
        DeleteConfirmationStories.addTo(registry);
        // App/Annotations/decorateComment
        DecorateCommentStories.addTo(registry);
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
