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

package stroom.gwt.workbench.client.app.query;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.HasDocumentRead;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.security.shared.DocumentPermission;
import stroom.widget.tab.client.presenter.TabData;

import com.gwtplatform.mvp.client.MyPresenterWidget;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.Map;
import java.util.function.Function;

/// What the document editor stories (`App/Editors/*`) of the query batch share: opening an
/// editor as Stroom's `DocumentPlugin` does, the Permissions tab's fixtures and the play helpers.
public final class DocumentEditors {

    /// The path of `DocPermissionResource.fetchDocumentUserPermissions()`.
    public static final String FETCH_PERMISSIONS_PATH = "/permission/doc/v1/fetchDocumentUserPermissions";
    /// The path of `DocPermissionResource.getDocUserPermissionsReport()`.
    public static final String PERMISSION_REPORT_PATH = "/permission/doc/v1/getDocUserPermissionsReport";
    /// The path of `ExplorerResource.decorate()`.
    public static final String DECORATE_PATH = "/explorer/v2/decorate";

    // The admin user owns the document
    private static final String OWNER_PERMISSIONS = """
            {
              "values": [
                {"userRef": {"uuid": "u-admin", "subjectId": "admin", "displayName": "admin", "group": false,
                  "enabled": true}, "permission": "OWNER"}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private DocumentEditors() {
        // Static utility
    }

    /// Adds the routes of an editor's Permissions tab, in which the `admin` user owns the document.
    ///
    /// @param builder The story's fixtures.
    /// @return The builder.
    public static RestFixtures.Builder ownerPermissions(final RestFixtures.Builder builder) {
        return builder
                .route(RequestMatcher.post(FETCH_PERMISSIONS_PATH), RestReply.json(OWNER_PERMISSIONS))
                .route(RequestMatcher.post(PERMISSION_REPORT_PATH),
                        RestReply.json("{\"explicitPermission\": \"OWNER\"}"));
    }

    /// Adds routes of `POST /explorer/v2/decorate` and `POST /explorer/v2/getFromDocRef` for each
    /// document given, which a document picker (`DocSelectionBoxPresenter`) sends when a document is
    /// selected, replying with the document as it is (it exists and the user may use it; an
    /// `ExplorerNode` has the same `type`, `uuid` and `name` members as a `DocRef`).
    ///
    /// @param builder  The story's fixtures.
    /// @param docRefs  The JSON of each document's `DocRef`, e.g.
    ///                 `{"type": "Feed", "uuid": "ds-1", "name": "My DataSource"}`.
    /// @return The builder.
    public static RestFixtures.Builder decorated(final RestFixtures.Builder builder, final String... docRefs) {
        for (final String docRef : docRefs) {
            final Object uuid = ((Map<?, ?>) JsonValues.parse(docRef)).get("uuid");
            builder.route(RequestMatcher.post(DECORATE_PATH)
                            .withJsonBodyContaining("{\"docRef\": {\"uuid\": \"" + uuid + "\"}}"),
                    RestReply.json(docRef));
            builder.route(RequestMatcher.post("/explorer/v2/getFromDocRef")
                            .withJsonBodyContaining("{\"uuid\": \"" + uuid + "\"}"),
                    RestReply.json(docRef));
        }
        return builder;
    }

    /// Opens a document's editor as Stroom's `DocumentPlugin` does: fetches the document, checks
    /// whether the user may edit it, reads it into the editor (read only if not), shows the
    /// editor as the harness's content and tells it that its tab is selected
    /// (`ContentTabSelectionChangeEvent`).
    ///
    /// @param harness   The story's harness.
    /// @param presenter The editor, e.g. from the batch's ginjector.
    /// @param docRef    The document.
    /// @param resource  The document's REST resource, e.g. `GWT.create(ViewResource.class)`.
    /// @param fetch     Fetches the document with the resource, e.g. `res -> res.fetch(uuid)`.
    /// @param <T>       The type of the resource.
    /// @param <D>       The type of the document.
    /// @param <P>       The type of the editor.
    public static <T extends DirectRestService, D, P extends MyPresenterWidget<?> & HasDocumentRead<D>> void open(
            final ScreenHarness harness,
            final P presenter,
            final DocRef docRef,
            final T resource,
            final Function<T, D> fetch) {
        harness.getRestFactory()
                .create(resource)
                .method(fetch)
                .onSuccess(doc -> harness.getSecurityContext().hasDocumentPermission(
                        docRef,
                        DocumentPermission.EDIT,
                        allowUpdate -> {
                            presenter.read(docRef, doc, !allowUpdate);
                            harness.addContent(presenter);
                            // As Stroom's content pane does when it selects the editor's tab
                            if (presenter instanceof TabData) {
                                ContentTabSelectionChangeEvent.fire(harness.getHasHandlers(), (TabData) presenter);
                            }
                        },
                        error -> harness.spy(ScreenHarness.ALERT_SPY, "ERROR: " + error.getMessage()),
                        presenter))
                .taskMonitorFactory(presenter)
                .exec();
    }

    /// Finds a sub-tab of a document editor by its label.
    /// Stroom's link tabs have no `role="tab"`, so they're found by their label.
    ///
    /// @param play  The play (or a part of it).
    /// @param label The tab's label, e.g. `Settings`.
    /// @return The tab's label.
    public static Query tab(final Play play, final String label) {
        return play.getByText(label, StroomDom.LINK_TAB_LABEL);
    }

    /// Checks that the story had no alerts and made no request without a fixture.
    ///
    /// @param play The play.
    public static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }
}
