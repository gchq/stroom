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

package stroom.gwt.workbench.client.app.screen;

import stroom.content.client.event.OpenContentTabEvent;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginEventManager;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;

import com.gwtplatform.mvp.client.HandlerContainer;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.Objects;
import java.util.function.Consumer;

/// Stroom's document plugin machinery for a story, as the app sets it up when its plugins start:
/// what goes through a document's plugin (found by type in the `DocumentPluginRegistry`) works as in
/// Stroom, e.g. `OpenDocumentEvent` and the editor's Save (`SaveDocumentEvent`, handled by the
/// `DocumentPluginEventManager`), a stepping element's code (`ElementPresenter` loads and saves it
/// with its document's plugin) and an embedded document created from a pipeline property.
///
/// A plugin registers itself for its type when it is created (`DocumentPlugin`'s constructor), so
/// get the event manager and each plugin a story needs from the story's ginjector (where the
/// content manager must be a singleton, as `AppModule` binds it) and pass them to
/// [#register(ScreenHarness, DocumentPluginEventManager, DocumentPlugin...)], which unbinds them
/// when the story renders again:
///
/// ```java
/// StoryDocumentPlugins.register(harness, injector.getDocumentPluginEventManager(),
///         injector.getPipelinePlugin(), injector.getXsltPlugin())
///         .showOpenedTabs();
/// harness.afterStartUp(() -> OpenDocumentEvent.fire(harness.getHasHandlers(), docRef, true));
/// ```
///
/// A plugin loads and saves its documents with `GET` and `PUT /{resource}/v1/{uuid}`; answer both
/// with [#documentRoutes(RestFixtures.Builder, String, String)].
public final class StoryDocumentPlugins {

    private final ScreenHarness harness;

    private StoryDocumentPlugins(final ScreenHarness harness) {
        this.harness = harness;
    }

    /// Registers the document plugins of a story: the event manager and the plugins (already
    /// registered for their types by being created) are unbound when the story renders again.
    ///
    /// @param harness      The story's harness.
    /// @param eventManager The injector's `DocumentPluginEventManager`.
    /// @param plugins      The injector's plugins the story needs, e.g. `XsltPlugin`.
    /// @return The plugins' set up, e.g. to show the tabs they open.
    public static StoryDocumentPlugins register(final ScreenHarness harness,
                                                final DocumentPluginEventManager eventManager,
                                                final DocumentPlugin<?>... plugins) {
        Objects.requireNonNull(harness);
        unbindOnCleanUp(harness::addCleanUp, eventManager);
        unbindOnCleanUp(harness::addCleanUp, plugins);
        return new StoryDocumentPlugins(harness);
    }

    /// Shows each tab a plugin opens (`OpenContentTabEvent`, which Stroom's content pane handles) as
    /// the story's content, filling the canvas, as the content pane shows the selected tab.
    ///
    /// @return This set up.
    public StoryDocumentPlugins showOpenedTabs() {
        harness.addRegistration(harness.getEventBus().addHandler(OpenContentTabEvent.getType(), event ->
                harness.addContent((PresenterWidget<?>) event.getLayer())));
        return this;
    }

    /// Adds the routes a document plugin loads and saves a document with: `GET` replies with the
    /// document, and `PUT` echoes the document saved, as the server does.
    ///
    /// @param builder The story's fixtures' builder.
    /// @param path    The document's path, e.g. `/xslt/v1/x1`.
    /// @param docJson The document's JSON.
    /// @return The builder.
    public static RestFixtures.Builder documentRoutes(final RestFixtures.Builder builder,
                                                      final String path,
                                                      final String docJson) {
        Objects.requireNonNull(docJson);
        return builder.get(path, RestReply.json(docJson))
                .put(path, request -> RestReply.json(request.getBody()));
    }

    /// Unbinds each handler container (e.g. a plugin) when the story renders again.
    ///
    /// @param addCleanUp Adds a clean up, e.g. `harness::addCleanUp`.
    /// @param containers The containers.
    static void unbindOnCleanUp(final Consumer<Runnable> addCleanUp, final HandlerContainer... containers) {
        for (final HandlerContainer container : containers) {
            Objects.requireNonNull(container);
            addCleanUp.accept(container::unbind);
        }
    }
}
