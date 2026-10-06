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

import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter;
import stroom.dictionary.client.presenter.DictionaryPresenter;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.job.client.presenter.JobPresenter;
import stroom.node.client.presenter.NodePresenter;
import stroom.security.client.presenter.UsersPresenter;
import stroom.task.client.presenter.TaskManagerPresenter;
import stroom.task.client.presenter.UserTaskManagerPresenter;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] that also creates Stroom's screens (presenters and their views) as Stroom's
/// GIN modules do, for stories to get their presenter from rather than creating it, and its
/// graph of child presenters and views, with `new`:
/// ```
/// final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
/// final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES).injector(injector).build();
/// final JobPresenter presenter = injector.getJobPresenter();
/// ```
/// Create one each time the story renders: its singletons belong to that rendering's harness.
///
/// Its modules mirror Stroom's (`XxxScreenModule` for Stroom's `XxxModule`), without the plugins
/// and app services, and [ScreenViewsModule] binds the shared widgets. Presenters are created as
/// in Stroom, so they are bound (`onBind`) as soon as they are created.
///
/// To add a screen: add a getter for its presenter here, under its area's comment, and, if GIN
/// reports a missing binding, add the mirror of the Stroom module that binds it to the
/// `@GinModules` list (one module per line, so that ports rarely touch the same lines). See
/// PORTING.md.
@GinModules({
        ScreenViewsModule.class,
        CredentialsScreenModule.class,
        DictionaryScreenModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        MonitoringScreenModule.class,
        SecurityScreenModule.class,
        TaskScreenModule.class,
})
public interface AppScreenGinjector extends ScreenGinjector {

    // Credentials

    /// @return A new credential picker dialog, as `ContentStoreContentPackDetailsPresenter` is
    /// given.
    CredentialsManagerDialogPresenter getCredentialsManagerDialogPresenter();

    // Documents and the explorer

    /// @return The document 'Info' dialog, shown by firing `ShowInfoDocumentDialogEvent`.
    InfoDocumentPresenter getInfoDocumentPresenter();

    /// @return The 'Edit Tags' dialog, shown by firing `ShowEditNodeTagsDialogEvent`.
    ExplorerNodeEditTagsPresenter getExplorerNodeEditTagsPresenter();

    /// @return The 'Remove Tags' dialog, shown by firing `ShowRemoveNodeTagsDialogEvent`.
    ExplorerNodeRemoveTagsPresenter getExplorerNodeRemoveTagsPresenter();

    // Monitoring

    /// @return The 'Jobs' tab.
    JobPresenter getJobPresenter();

    /// @return The 'Nodes' tab.
    NodePresenter getNodePresenter();

    /// @return The 'Server Tasks' tab.
    TaskManagerPresenter getTaskManagerPresenter();

    /// @return The user's task manager dialog, shown by firing `OpenUserTaskManagerEvent`.
    UserTaskManagerPresenter getUserTaskManagerPresenter();

    // Security

    /// @return The 'Users' tab.
    UsersPresenter getUsersPresenter();

    // Dictionaries

    /// @return A Dictionary's editor tab, as `DictionaryPlugin` creates it.
    DictionaryPresenter getDictionaryPresenter();
}
