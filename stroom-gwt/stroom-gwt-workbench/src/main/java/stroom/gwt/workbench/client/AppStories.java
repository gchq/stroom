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

import stroom.gwt.workbench.client.app.main.AboutDialogStories;
import stroom.gwt.workbench.client.app.main.RecentItemsDialogStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers the GWT ports of the React Storybook's `App/*` stories (107 components,
/// 516 stories in the React Storybook), in the React sidebar's order.
///
/// ## Adding a story class
///
/// 1. Write the stories class in the `stroom.gwt.workbench.client.app` package (see
///    `widgets/ButtonStories` for an example), giving it a
///    `public static void addTo(StoryRegistry registry)` method that calls
///    `registry.component("<React title>", XxxStories.class)` with exactly the React `title`,
///    and `.story("<React export name>", ...)` for each story, so that the story ids match the
///    React ones (they are checked by `TestReactStoryCoverage`).
/// 2. Call it in [#addTo] on the line after the comment holding its React title, e.g.
///    ```
///    // App/Main/AboutDialog
///    XxxStories.addTo(registry);
///    ```
///    There is a comment for every React component, so each porter edits a different line, which
///    keeps merge conflicts to a minimum. Leave the comments in place.
///
/// The comments are the titles in `src/test/resources/react-stories.json`. A React component
/// added since then can be added in its sidebar position, or before `return registry;`.
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
        // App/Main/AnnotationEditor
        // App/Main/AnnotationTagScreen
        // App/Main/ApiErrorAlertHost
        // App/Main/ApiKeysScreen
        // App/Main/AppPermissionsScreen
        // App/Main/AppShell
        // App/Main/BeginSteppingDialog
        // App/Main/BrowseAnnotationsScreen
        // App/Main/CachesScreen
        // App/Main/ChangePasswordDialog
        // App/Main/ContentStoreScreen
        // App/Main/ContentTabPane
        // App/Main/ContentTemplatesScreen
        // App/Main/CreateDocumentDialog
        // App/Main/CredentialPickerDialog
        // App/Main/CredentialsScreen
        // App/Main/DataReceiptRulesScreen
        // App/Main/DataRetentionScreen
        // App/Main/DataScreen
        // App/Main/DataVolumesScreen
        // App/Main/DatabaseTablesScreen
        // App/Main/DependenciesScreen
        // App/Main/DependencyInfoDialog
        // App/Main/DocInfoDialog
        // App/Main/DocumentCreatePermissionsGrid
        // App/Main/DocumentPermissionsScreen
        // App/Main/EditAccountDialog
        // App/Main/EditTagsDialog
        // App/Main/ExecutionSchedulesScreen
        // App/Main/FindDialog
        // App/Main/FindInContentDialog
        // App/Main/ImportDialog
        // App/Main/IndexVolumesScreen
        // App/Main/JobsScreen
        // App/Main/ManageActivityDialog
        // App/Main/NodeGroupMembersDialog
        // App/Main/NodeGroupsScreen
        // App/Main/NodesScreen
        // App/Main/PipelineEditor
        // App/Main/PipelineTree
        // App/Main/ProcessorFilterEditDialog
        // App/Main/ProcessorFilterList
        // App/Main/ProcessorLimitsDialog
        // App/Main/ProcessorProfilesScreen
        // App/Main/PropertiesScreen
        // App/Main/RecentItemsDialog
        RecentItemsDialogStories.addTo(registry);
        // App/Main/ResultStoresDialog
        // App/Main/ServerTasksScreen
        // App/Main/SigningKeysScreen
        // App/Main/StepFilterDialog
        // App/Main/SteppingScreen
        // App/Main/TabSessionChooserDialog
        // App/Main/TracesScreen
        // App/Main/UserAccessScreen
        // App/Main/UserGroupsScreen
        // App/Main/UserProfileScreen
        // App/Main/UserTabScreen
        // App/Main/UserTaskManagerDialog
        // App/Main/UsersScreen
        // App/Main/VisualisationAssets
        // App/Main/WelcomeScreen
        // App/Main/currentActivity
        // App/Main/docPlugin
        // App/Main/Activity Chooser
        // App/Editors/AnalyticRuleEditor
        // App/Editors/DashboardEditor
        // App/Editors/DataGenEditor
        // App/Editors/DocumentationEditor
        // App/Editors/ElasticClusterEditor
        // App/Editors/FolderEditor
        // App/Editors/GitRepoEditor
        // App/Editors/OpenAiModelEditor
        // App/Editors/PathwaysEditor
        // App/Editors/PlanBEditor
        // App/Editors/QueryEditor
        // App/Editors/ReportEditor
        // App/Editors/StatisticStoreEditor
        // App/Editors/ViewEditor
        // App/Editors/VisualisationEditor
        // App/Editors/CodeDocumentEditor
        // App/AI/AskAiChatPanel
        // App/AI/AskStroomAiDialog
        // App/Dashboard/TableFilterSettings
        // App/Dashboard/QueryResultVis
        // App/Dashboard/PausePolling
        // App/Data/DataUploadDialog
        // App/Data/MetaBrowser
        // App/Data/ProcessChoiceDialog
        // App/Dictionary/DictionaryEditor
        // App/Security/DocumentPermissionsTab
        // App/Index/ElasticIndexEditor
        // App/Index/IndexEditor
        // App/Index/SolrIndexEditor
        // App/Feed/FeedEditor
        // App/Query/QueryResultsTable
        // App/Core/appPermissions
        // App/Core/deleteConfirmation
        // App/Annotations/decorateComment
        // App/IdP/AuthenticationError
        // App/IdP/ChangePassword
        // App/IdP/CurrentPassword
        // App/IdP/EmailResetPassword
        // App/IdP/ResetPassword
        // App/IdP/Login
        // App/IdP/SignInFlow
        return registry;
    }
}
