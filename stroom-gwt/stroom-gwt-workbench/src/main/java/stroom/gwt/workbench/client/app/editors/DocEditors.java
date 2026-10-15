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

package stroom.gwt.workbench.client.app.editors;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.dispatch.client.RestFactory.MethodExecutor;
import stroom.docref.DocRef;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.event.client.StaticEventBus;
import stroom.gwt.workbench.client.app.gin.editors.EditorsScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.security.shared.DocumentPermission;
import stroom.widget.help.client.presenter.HelpManager;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/// Opens a document in its real Stroom editor (a `DocPresenter`, usually a `DocTabPresenter`) for
/// the `editors` batch of screen stories, as a `DocumentPlugin` does: it fetches the document,
/// checks whether the user may edit it, reads it into the editor and shows the editor as the
/// story's content. Saving (the toolbar's Save, which fires `SaveDocumentEvent`) is answered as
/// `DocumentPlugin.save` answers it: the editor writes the document, which is sent to the
/// document's `update` endpoint, then the editor's post save callback (if any) runs, and the
/// reply is read back into the editor.
///
/// Also holds the fixtures the editor stories share: the Permissions tab's routes (the user
/// `admin` is the document's OWNER).
public final class DocEditors {

    // DocPermissionResource.fetchDocumentUserPermissions(): admin is the document's OWNER
    private static final String DOC_PERMISSIONS = """
            {
              "values": [
                {"userRef": {"uuid": "u-admin", "subjectId": "admin", "displayName": "admin",
                  "group": false, "enabled": true}, "permission": "OWNER"}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private DocEditors() {
        // Static utility
    }

    /// Adds the routes every editor's Permissions tab uses: `admin` is the OWNER.
    ///
    /// @param builder The story's fixtures.
    /// @return The builder.
    public static RestFixtures.Builder permissionRoutes(final RestFixtures.Builder builder) {
        return builder
                .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json(DOC_PERMISSIONS))
                .route(RequestMatcher.post("/permission/doc/v1/getDocUserPermissionsReport"),
                        RestReply.json("{\"explicitPermission\": \"OWNER\"}"));
    }

    /// Renders an editor story: creates the batch's injector and a harness (with Stroom's real
    /// alerts, as the editors' plays read them), gives the user VIEW or EDIT permission on every
    /// document, and once Stroom has started (the editors read the user's preferences) runs the
    /// opener, which usually calls [#open].
    ///
    /// @param context  The story's context.
    /// @param fixtures The story's fixtures.
    /// @param readOnly Whether the user may only view the documents.
    /// @param options  Other options for the harness, e.g. app permissions.
    /// @param opener   Opens the story's editor.
    /// @return The story's widget.
    public static Widget render(final StoryContext context,
                                final RestFixtures fixtures,
                                final boolean readOnly,
                                final Consumer<ScreenHarness.Builder> options,
                                final Opener opener) {
        final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts();
        options.accept(builder);
        final ScreenHarness harness = builder.build();
        // The user owns the document (or may only view it)
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.OWNER);
        // As Stroom's app does at start-up (eager singletons): form help buttons fire their
        // events on the static event bus, which the help manager shows
        new StaticEventBus(harness.getEventBus());
        new HelpManager(harness.getEventBus());
        harness.afterStartUp(() -> opener.open(harness, injector));
        return harness.asWidget();
    }

    /// As [#render(StoryContext, RestFixtures, boolean, Consumer, Opener)], with the harness's
    /// default options.
    ///
    /// @param context  The story's context.
    /// @param fixtures The story's fixtures.
    /// @param readOnly Whether the user may only view the documents.
    /// @param opener   Opens the story's editor.
    /// @return The story's widget.
    public static Widget render(final StoryContext context,
                                final RestFixtures fixtures,
                                final boolean readOnly,
                                final Opener opener) {
        return render(context, fixtures, readOnly, builder -> {
        }, opener);
    }

    /// Adds the routes that document selection boxes (e.g. an Elastic index's cluster, a data
    /// generator's feed) call to show their document: `POST /explorer/v2/decorate` (filling in
    /// the document's name) and `POST /explorer/v2/getFromDocRef` (its explorer node), each
    /// replying with the document asked about.
    ///
    /// @param builder The story's fixtures.
    /// @return The builder.
    public static RestFixtures.Builder docSelectionRoutes(final RestFixtures.Builder builder) {
        return builder
                .post("/explorer/v2/decorate", request -> {
                    final Object body = JsonValues.parse(request.getBody());
                    final Object docRef = body instanceof final Map<?, ?> map
                            ? map.get("docRef")
                            : null;
                    return docRef instanceof final Map<?, ?> docRefMap
                            ? RestReply.json(toDocRefJson(docRefMap, ""))
                            : RestReply.noContent();
                })
                .post("/explorer/v2/getFromDocRef", request -> {
                    final Object docRef = JsonValues.parse(request.getBody());
                    return docRef instanceof final Map<?, ?> docRefMap
                            ? RestReply.json(toDocRefJson(docRefMap, ", \"depth\": 0, \"rootNodeUuid\": \"0\""))
                            : RestReply.noContent();
                });
    }

    // A DocRef's JSON (or an explorer node's, with the extra members)
    private static String toDocRefJson(final Map<?, ?> docRef, final String extra) {
        return "{\"type\": \"" + docRef.get("type") + "\", \"uuid\": \"" + docRef.get("uuid")
               + "\", \"name\": \"" + docRef.get("name") + "\"" + extra + "}";
    }

    /// Opens a document in its editor, as `DocumentPlugin.open` does (see the class description),
    /// and shows the editor as the story's content. Call it in [ScreenHarness#afterStartUp], as
    /// the editors read the user's preferences.
    ///
    /// @param harness   The story's harness.
    /// @param docRef    The document to open.
    /// @param presenter The document's editor, from the story's injector.
    /// @param resource  How to fetch and update the document.
    /// @param <D>       The document type.
    public static <D> void open(final ScreenHarness harness,
                                final DocRef docRef,
                                final DocPresenter<?, D> presenter,
                                final DocResource<D> resource) {
        harness.addRegistration(harness.getEventBus().addHandler(SaveDocumentEvent.getType(), event -> {
            if (event.getTabData() == presenter) {
                save(harness, docRef, presenter, resource);
            }
        }));
        resource.fetch(harness.getRestFactory())
                .onSuccess(doc -> {
                    if (presenter instanceof final DocTabPresenter<?, ?> docTabPresenter) {
                        docTabPresenter.getDefaultTab().ifPresent(docTabPresenter::selectTab);
                    }
                    harness.getSecurityContext().hasDocumentPermission(
                            docRef,
                            DocumentPermission.EDIT,
                            allowUpdate -> {
                                presenter.read(docRef, doc, !allowUpdate);
                                harness.addContent(presenter);
                            },
                            error -> AlertEvent.fireErrorFromException(harness.getHasHandlers(), error, null),
                            presenter);
                })
                .onFailure(error -> AlertEvent.fireError(harness.getHasHandlers(),
                        "Unable to load document " + docRef,
                        error.getMessage(),
                        null))
                .taskMonitorFactory(presenter)
                .exec();
    }

    // As DocumentPlugin.save
    private static <D> void save(final ScreenHarness harness,
                                 final DocRef docRef,
                                 final DocPresenter<?, D> presenter,
                                 final DocResource<D> resource) {
        if (presenter.isDirty()) {
            final D document = presenter.write(presenter.getEntity());
            if (document != null) {
                final Consumer<D> afterSave = saved -> presenter.read(docRef, saved, presenter.isReadOnly());
                final BiConsumer<D, Consumer<D>> postSaveCallback = presenter.getPostSaveCallback();
                resource.update(harness.getRestFactory(), document)
                        .onSuccess(saved -> {
                            // As the plugins with a post save callback (VisualisationPlugin) do
                            if (postSaveCallback != null) {
                                postSaveCallback.accept(saved, afterSave);
                            } else {
                                afterSave.accept(saved);
                            }
                        })
                        .onFailure(error -> AlertEvent.fireError(harness.getHasHandlers(),
                                "Unable to save document " + docRef,
                                error.getMessage(),
                                null))
                        .taskMonitorFactory(presenter)
                        .exec();
            }
        }
    }

    /// A sub-tab of a document editor.
    /// Stroom's link tabs have no `role="tab"`, so they're found by their label.
    ///
    /// @param play  The play (or a scope of it).
    /// @param label The tab's label.
    /// @return The tab's label element.
    public static Query tab(final Play play, final String label) {
        return play.getByText(label, StroomDom.LINK_TAB_LABEL);
    }

    /// Checks that the story made no alerts and no requests the fixtures didn't answer.
    ///
    /// @param play The play.
    public static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    /// Checks that the story made no requests the fixtures didn't answer (for a story whose alerts
    /// are expected).
    ///
    /// @param play The play.
    public static void expectNoUnhandledRequests(final Play play) {
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    /// Opens a story's editor, once Stroom has started.
    public interface Opener {

        /// @param harness  The story's harness.
        /// @param injector The story's injector, to get the editor from.
        void open(ScreenHarness harness, EditorsScreenGinjector injector);
    }

    /// How a story fetches and updates its document, e.g. with Stroom's `XxxResource`, as the
    /// document's plugin does (`XxxPlugin.load` and `save`).
    ///
    /// @param <D> The document type.
    public interface DocResource<D> {

        /// @param restFactory The harness's REST factory.
        /// @return The request that fetches the document.
        MethodExecutor<?, D> fetch(RestFactory restFactory);

        /// @param restFactory The harness's REST factory.
        /// @param document    The document to save.
        /// @return The request that updates the document, replying with the saved document.
        MethodExecutor<?, D> update(RestFactory restFactory, D document);

        /// Creates a resource from two functions, e.g.
        /// ```
        /// DocResource.of(
        ///         restFactory -> restFactory.create(RESOURCE).method(res -> res.fetch(uuid)),
        ///         (restFactory, doc) -> restFactory.create(RESOURCE).method(res -> res.update(uuid, doc)))
        /// ```
        ///
        /// @param fetch  Creates the request that fetches the document.
        /// @param update Creates the request that updates the document.
        /// @param <D>    The document type.
        /// @return The resource.
        static <D> DocResource<D> of(final Function<RestFactory, MethodExecutor<?, D>> fetch,
                                     final BiFunction<RestFactory, D, MethodExecutor<?, D>> update) {
            return new DocResource<>() {
                @Override
                public MethodExecutor<?, D> fetch(final RestFactory restFactory) {
                    return fetch.apply(restFactory);
                }

                @Override
                public MethodExecutor<?, D> update(final RestFactory restFactory, final D document) {
                    return update.apply(restFactory, document);
                }
            };
        }
    }
}
