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

package stroom.gwt.workbench.framework.client;

import stroom.gwt.workbench.framework.client.manager.WorkbenchManager;
import stroom.gwt.workbench.framework.client.preview.RunnerHooks;
import stroom.gwt.workbench.framework.client.preview.RunnerJson;
import stroom.gwt.workbench.framework.client.preview.StoryPreview;
import stroom.gwt.workbench.framework.client.story.StoryDecorator;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.gwt.workbench.framework.client.story.StoryUrls;

import com.google.gwt.user.client.Window;

import java.util.Objects;

/// Starts the workbench. A GWT module containing stories calls this from its entry point, e.g.
/// ```
/// public void onModuleLoad() {
///     Workbench.start(AllStories.create(), new MyThemeDecorator());
/// }
/// ```
///
/// The same module is loaded by both of the host pages:
///
/// * `index.html` - the manager (sidebar, toolbar, addon panel), which needs no content of its
///   own as the workbench adds it.
/// * `iframe.html` - the preview, which renders a single story. This page should load any
///   styles/scripts the stories need.
///
/// Both pages expose an index of all the stories as `window.__workbenchIndex`, and the preview
/// exposes the progress of the story's play function, for the test runner (see [RunnerHooks]).
public final class Workbench {

    private Workbench() {
        // Static utility
    }

    /// Starts the workbench with no decorator.
    ///
    /// @param registry All the stories.
    public static void start(final StoryRegistry registry) {
        start(registry, StoryDecorator.NONE);
    }

    /// Starts the workbench.
    ///
    /// @param registry  All the stories.
    /// @param decorator Wraps each story's widget in the preview.
    public static void start(final StoryRegistry registry, final StoryDecorator decorator) {
        Objects.requireNonNull(registry);
        Objects.requireNonNull(decorator);
        // For the test runner, see RunnerHooks
        RunnerHooks.publishIndex(RunnerJson.index(registry));
        if (StoryUrls.isPreviewPage(Window.Location.getPath())) {
            new StoryPreview(registry, decorator).render(
                    Window.Location.getParameter(StoryUrls.ID_PARAM));
        } else {
            new WorkbenchManager(registry).start();
        }
    }
}
