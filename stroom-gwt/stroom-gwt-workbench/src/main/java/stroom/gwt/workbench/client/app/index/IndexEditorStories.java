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

package stroom.gwt.workbench.client.app.index;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.editors.DocEditors;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.index.client.presenter.IndexPresenter;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Index/IndexEditor` stories, showing Stroom's real [IndexPresenter] (a Lucene index's
/// tab: Shards for users with Manage Index Shards, Fields, Settings, Documentation and Permissions)
/// with fake REST replies.
///
/// As `IndexPlugin` does, the story fetches the document (`GET /index/v2/{uuid}`) and reads it into
/// the editor ([DocEditors#open]). The fields are `POST /index/v2/findFields` (the plays check the
/// last request with the request spy), the shards `POST /index/v2/shard/find` and the volume groups
/// `POST /index/volumeGroup/v2/find`; the Shards tab needs the `MANAGE_INDEX_SHARDS` app
/// permission, set with the harness's app permissions.
public final class IndexEditorStories {

    private static final DocRef DOC_REF = new DocRef(LuceneIndexDoc.TYPE, "index-events", "Events Index");

    // IndexResource.fetch(): the index
    private static final String DOC = """
            {"type": "Index", "uuid": "index-events", "name": "Events Index", "description": "# Events index",
              "maxDocsPerShard": 1000000000, "shardsPerPartition": 1, "partitionBy": "MONTH",
              "partitionSize": 1, "timeField": "EventTime", "retentionDayAge": 365,
              "volumeGroupName": "Default"}""";

    // IndexResource.findFields(): the fields
    private static final String FIELDS = """
            {"values": [
                {"fldName": "EventTime", "fldType": "DATE", "indexed": true, "stored": true,
                  "termPositions": false, "analyzerType": "KEYWORD"},
                {"fldName": "UserId", "fldType": "TEXT", "indexed": true, "stored": false,
                  "analyzerType": "ALPHA_NUMERIC"},
                {"fldName": "Embedding", "fldType": "DENSE_VECTOR", "indexed": true,
                  "denseVectorFieldConfig": {"vectorSimilarityFunction": "COSINE", "segmentSize": 1500}}],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}}""";

    // IndexResource.find() (shards): a shard, with 2024-02-01T09:33:20Z as its commit time
    private static final String SHARDS = """
            {"values": [{"id": 1, "nodeName": "node1", "partition": "2024-01", "status": "OPEN",
                "documentCount": 12345, "fileSize": 500000, "volume": {"id": 1, "path": "/data/index/1"},
                "commitMs": 1706780000000, "commitDurationMs": 1500, "commitDocumentCount": 100,
                "indexVersion": "9.11.1", "indexUuid": "index-events"}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    // IndexVolumeGroupResource.find(): the volume groups
    private static final String VOLUME_GROUPS = """
            {"values": [{"id": 1, "name": "Default"}, {"id": 2, "name": "Fast"}, {"id": 3, "name": "Archive"}],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}}""";

    private static final String FIND_FIELDS = "/index/v2/findFields";

    private static final RestFixtures FIXTURES = DocEditors.permissionRoutes(RestFixtures.builder())
            .get("/index/v2/index-events", RestReply.json(DOC))
            .put("/index/v2/index-events", request -> RestReply.json(request.getBody()))
            .post(FIND_FIELDS, RestReply.json(FIELDS))
            .post("/index/v2/shard/find", RestReply.json(SHARDS))
            .post("/index/volumeGroup/v2/find", RestReply.json(VOLUME_GROUPS))
            .build();

    private IndexEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Index/IndexEditor", IndexEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Without Manage Index Shards: Fields (default), Settings, Documentation, Permissions
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    for (final String label : new String[]{"Fields", "Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.expect(play.queryByText("Shards", ".linkTab-label")).toBeNull();
                    // Fields is the default tab, sorted by Name, ascending and case sensitive
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
                    play.expect(play.getByText("UserId")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenLastCalledWith(
                            RequestMatcher.post(FIND_FIELDS).withJsonBodyContaining(
                                    "{\"sortList\": [{\"id\": \"Name\", \"desc\": false, \"ignoreCase\": false}]}")
                                    .toSpyMatcher()));
                    // Clicking the ascending Name header sorts descending
                    play.click(play.getByText("Name"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenLastCalledWith(
                            RequestMatcher.post(FIND_FIELDS).withJsonBodyContaining(
                                    "{\"sortList\": [{\"id\": \"Name\", \"desc\": true, \"ignoreCase\": false}]}")
                                    .toSpyMatcher()));
                    play.click(DocEditors.tab(play, "Settings"));
                    play.expect(play.findByText("Max Docs Per Shard", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Volume Group", "label")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // With Manage Index Shards, Shards is the first tab and selected, with Flush and
                // Delete
                .story("Shards", context -> render(context, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByText("2024-01")).toBeInTheDocument());
                    play.expect(play.getByRole("button", "Flush Selected Shards")).toHaveClass("disabled");
                    play.expect(play.getByRole("button", "Delete Selected Shards")).toHaveClass("disabled");
                    // Shards are selected with their row's tick box
                    play.click(play.within(play.getByText("2024-01").closest("tr")).querySelector(".tickBox"));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Flush Selected Shards"))
                            .not().toHaveClass("disabled"));
                    play.expect(play.getByRole("button", "Delete Selected Shards")).not().toHaveClass("disabled");
                    // The info column's popup shows what isn't a column
                    play.click(play.querySelector(".svgCell-icon"));
                    final Play info = play.within(screen.findByText("Index UUID").closest("table"));
                    play.expect(info.getByText("Commit Document Count")).toBeInTheDocument();
                    play.expect(info.getByText("/data/index/1")).toBeInTheDocument();
                    // Commit Duration is formatted, not raw milliseconds
                    play.expect(info.queryByText("1500")).toBeNull();
                    // Dates use the user's date time pattern (the default, an ISO instant)
                    play.expect(info.getByText(TextMatch.regex(
                            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(?:Z|[+-]\\d{2}:?\\d{2})$", "")))
                            .toBeInTheDocument();
                    // File Size is an IEC byte size
                    play.expect(info.queryByText("500000")).toBeNull();
                    play.expect(info.getByText("488K")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // Editing a dense vector field shows the dense vector settings
                .story("DenseVectorField", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByText("Embedding")).toBeInTheDocument());
                    // FieldType.getDisplayValue() is the type's name, not spaced
                    play.expect(play.getByText("DenseVector")).toBeInTheDocument();
                    play.expect(play.queryByText("Dense Vector")).toBeNull();
                    play.click(play.getByText("Embedding"));
                    play.click(play.getByRole("button", "Edit Field"));
                    play.waitFor(() -> play.expect(screen.getByText("Vector Similarity Function Type", "label"))
                            .toBeInTheDocument());
                    play.expect(screen.getByText("Embedding Model", "label")).toBeInTheDocument();
                    play.expect(screen.getByText("Nearest Neighbour Count", "label")).toBeInTheDocument();
                    play.expect(screen.getByText("Minimum Rerank Score", "label")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // The Fields tab (the default) as an editable partner for FieldsReadOnly
                .story("FieldsEditable", context -> render(context, false, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // OK on a new field with no name says why and leaves the dialog usable (its OK and
                // Cancel once stayed disabled with no message, as the validation error went uncaught)
                .story("NewFieldWithoutName", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
                    play.click(play.getByRole("button", "New Field"));
                    final Play dialog = screen.within(screen.findByText("New Field", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("WARN: An index field must have a name"));
                    play.click(screen.findByRole("button", StroomDom.button("Close")));
                    // The dialog can be used again: Cancel closes it, and nothing was added
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("New Field", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY))
                            .not().toHaveBeenCalledWith(RequestMatcher.post("/index/v2/addField").toSpyMatcher());
                    DocEditors.expectNoUnhandledRequests(play);
                })
                // The Fields tab (the default) when the user may only view the index
                .story("FieldsReadOnly", context -> render(context, false, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab as an editable partner for SettingsReadOnly
                .story("SettingsEditable", context -> render(context, false, false))
                .withPlay(play -> {
                    openSettings(play);
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab when the user may only view the index
                .story("SettingsReadOnly", context -> render(context, false, true))
                .withPlay(play -> {
                    openSettings(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // Opens the Settings tab and waits for its form
    private static void openSettings(final Play play) {
        play.waitFor(() -> play.expect(DocEditors.tab(play, "Settings")).toBeInTheDocument());
        play.click(DocEditors.tab(play, "Settings"));
        play.waitFor(() -> play.expect(play.getByText("Max Docs Per Shard", "label")).toBeInTheDocument());
        play.expect(play.getByText("Volume Group", "label")).toBeInTheDocument();
    }

    private static Widget render(final StoryContext context, final boolean manageShards) {
        return render(context, manageShards, false);
    }

    private static Widget render(final StoryContext context, final boolean manageShards, final boolean readOnly) {
        final IndexResource resource = GWT.create(IndexResource.class);
        return DocEditors.render(context, FIXTURES, readOnly,
                // The app permissions: Manage Index Shards or nothing
                builder -> {
                    if (manageShards) {
                        builder.appPermissions(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION);
                    } else {
                        builder.appPermissions();
                    }
                },
                (harness, injector) -> DocEditors.open(harness,
                        DOC_REF,
                        injector.getIndexPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
