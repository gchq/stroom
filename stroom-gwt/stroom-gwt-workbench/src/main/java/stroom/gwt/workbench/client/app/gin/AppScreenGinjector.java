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


package stroom.gwt.workbench.client.app.gin;

import stroom.credentials.client.component.CredentialsManagerDialogController;
import stroom.dictionary.client.component.DictionaryEditor;
import stroom.entity.client.component.InfoDocumentController;
import stroom.explorer.client.component.ExplorerNodeEditTagsController;
import stroom.explorer.client.component.ExplorerNodeRemoveTagsController;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.job.client.component.JobsController;
import stroom.node.client.component.NodesController;
import stroom.security.client.component.UsersController;
import stroom.task.client.component.ServerTasksController;
import stroom.task.client.component.UserTaskManagerController;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] that also creates Stroom's screens (controllers and their components) as Stroom's
/// GIN modules do, for stories to get their controller from rather than creating it, and its
/// graph of child controllers and components, with `new`:
/// ```
/// final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final JobsController controller = injector.getJobsController();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
///
/// Its modules mirror Stroom's (`XxxScreenModule` for Stroom's `XxxModule`), without the plugins
/// and app services. Controllers are created as in Stroom, so they are bound (`onBind`) as soon as they are created.
///
/// To add a screen: add a getter for its controller here, under its area's comment, and, if GIN
/// reports a missing binding, add the mirror of the Stroom module that binds it to the
/// `@GinModules` list (one module per line, so that parallel work rarely touches the same lines).
/// See WRITING-STORIES.md.
@GinModules({
})
public interface AppScreenGinjector extends ScreenGinjector {

    // Credentials

    /// @return A new credential picker dialog, as `ContentStoreContentPackDetailsController` is
    /// given.
    CredentialsManagerDialogController getCredentialsManagerDialogController();

    // Documents and the explorer

    /// @return The document 'Info' dialog, shown by firing `ShowInfoDocumentDialogEvent`.
    InfoDocumentController getInfoDocumentController();

    /// @return The 'Edit Tags' dialog, as `NavigationPlugin` shows it for `ShowEditNodeTagsDialogEvent`.
    ExplorerNodeEditTagsController getExplorerNodeEditTagsController();

    /// @return The 'Remove Tags' dialog, as `NavigationPlugin` shows it for
    /// `ShowRemoveNodeTagsDialogEvent`.
    ExplorerNodeRemoveTagsController getExplorerNodeRemoveTagsController();

    // Monitoring

    /// @return The 'Jobs' tab.
    JobsController getJobsController();

    /// @return The 'Nodes' tab.
    NodesController getNodesController();

    /// @return The 'Server Tasks' tab.
    ServerTasksController getServerTasksController();

    /// @return The user's task manager dialog, as `TaskManagerPlugin` shows it on `OpenUserTaskManagerEvent`.
    UserTaskManagerController getUserTaskManagerController();

    // Security

    /// @return The 'Users' tab.
    UsersController getUsersController();

    // Dictionaries

    /// @return A Dictionary's editor, which `DictionaryPlugin` opens in a `DocumentEditorHost`.
    DictionaryEditor getDictionaryEditor();
}
