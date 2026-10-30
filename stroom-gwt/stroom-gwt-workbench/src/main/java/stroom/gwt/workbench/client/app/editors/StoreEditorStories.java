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

import stroom.docref.DocRef;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapEventStoreResource;
import stroom.gwt.workbench.client.app.gin.editors.EditorsScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.sqlstore.shared.SqlTemporalStoreDocResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Editors/SqlTemporalStoreEditor` and `App/Editors/FloorMapEventStoreEditor`,
/// showing Stroom's real editors of a SQL temporal store and of a floor map event store (their
/// Settings, Data, Documentation and Permissions tabs) with fake REST replies. As their plugins do,
/// a story fetches the document, checks the user may edit it and reads it into the editor; their
/// tabs have stories of their own (`SqlTemporalStoreSettings`, `FloorMapEventStoreSettings`).
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `GET /sqlTemporalStoreDoc/v1/{uuid}` | the SQL temporal store |
/// | `POST /sqltemporalstore/v1/count` | its Settings tab's entry count |
/// | `GET /floorMapEventStore/v1/{uuid}` | the floor map event store |
/// | `POST /permission/doc/v1/fetchDocumentUserPermissions` and the report | the Permissions tab |
public final class StoreEditorStories {

    private static final String SQL_UUID = "sql-store-1";
    private static final String EVENTS_UUID = "event-store-1";

    private static final String SQL_DOC = """
            {"type": "SqlTemporalStore", "uuid": "sql-store-1", "name": "Office states",
              "description": "# The office's states"}""";
    private static final String EVENTS_DOC = """
            {"type": "FloorMapEventStore", "uuid": "event-store-1", "name": "Office events",
              "stateType": "TEMPORAL_STATE", "settings": {"type": "temporalState"},
              "eventExpiry": {"time": 2, "timeUnit": "DAYS"},
              "description": "# The office's events"}""";

    private static final RestFixtures FIXTURES = DocumentEditors.ownerPermissions(RestFixtures.builder())
            .get("/sqlTemporalStoreDoc/v1/" + SQL_UUID, RestReply.json(SQL_DOC))
            .post("/sqltemporalstore/v1/count", RestReply.json("42"))
            .get("/floorMapEventStore/v1/" + EVENTS_UUID, RestReply.json(EVENTS_DOC))
            .build();

    private StoreEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/SqlTemporalStoreEditor", StoreEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Its tabs, Settings first: the store's entry count, and Reset Store
                .story("Default", context -> renderSqlStore(context, false))
                .withPlay(play -> {
                    expectTabs(play);
                    play.waitFor(() -> play.expect(play.getByText("42")).toBeVisible());
                    play.expect(play.getByRole("button", StroomDom.button("Reset Store"))).toBeEnabled();
                    DocumentEditors.expectNoProblems(play);
                })
                // Read only, as the tab bar says
                .story("ReadOnly", context -> renderSqlStore(context, true))
                .withPlay(play -> {
                    expectTabs(play);
                    play.waitFor(() -> play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note"))
                            .toBeVisible());
                    DocumentEditors.expectNoProblems(play);
                });

        registry.component("App/Editors/FloorMapEventStoreEditor", StoreEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Its tabs, Settings first: how long an event shows on the map, and Plan B's settings
                .story("Default", context -> renderEventStore(context, false))
                .withPlay(play -> {
                    expectTabs(play);
                    play.waitFor(() -> play.expect(play.getByText("Event Expiry", "label")).toBeVisible());
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocumentEditors.expectNoProblems(play);
                })
                // Read only, as the tab bar says
                .story("ReadOnly", context -> renderEventStore(context, true))
                .withPlay(play -> {
                    expectTabs(play);
                    play.waitFor(() -> play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note"))
                            .toBeVisible());
                    DocumentEditors.expectNoProblems(play);
                });
    }

    private static void expectTabs(final Play play) {
        for (final String label : new String[]{"Settings", "Data", "Documentation", "Permissions"}) {
            play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
        }
    }

    private static Widget renderSqlStore(final StoryContext context, final boolean readOnly) {
        final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
        final ScreenHarness harness = harness(context, injector, readOnly);
        final DocRef docRef = new DocRef(SqlTemporalStoreDoc.TYPE, SQL_UUID, "Office states");
        final SqlTemporalStoreDocResource resource = GWT.create(SqlTemporalStoreDocResource.class);
        // Opened once Stroom has started, as SqlTemporalStorePlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getSqlTemporalStoreEditor(), docRef,
                resource, res -> res.fetch(SQL_UUID)));
        return harness.asWidget();
    }

    private static Widget renderEventStore(final StoryContext context, final boolean readOnly) {
        final EditorsScreenGinjector injector = GWT.create(EditorsScreenGinjector.class);
        final ScreenHarness harness = harness(context, injector, readOnly);
        final DocRef docRef = new DocRef(FloorMapEventStoreDoc.TYPE, EVENTS_UUID, "Office events");
        final FloorMapEventStoreResource resource = GWT.create(FloorMapEventStoreResource.class);
        // Opened once Stroom has started, as FloorMapEventStorePlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getFloorMapEventStoreEditor(), docRef,
                resource, res -> res.fetch(EVENTS_UUID)));
        return harness.asWidget();
    }

    private static ScreenHarness harness(final StoryContext context,
                                         final EditorsScreenGinjector injector,
                                         final boolean readOnly) {
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        return harness;
    }
}
