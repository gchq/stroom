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
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.search.solr.client.presenter.SolrIndexPresenter;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Stories matching `App/Index/SolrIndexEditor` in the React Storybook, showing Stroom's real
/// [SolrIndexPresenter] (a Solr index's tab: Fields, Settings, Documentation and Permissions) with
/// fake REST replies.
///
/// As `SolrIndexPlugin` does, the story fetches the document (`GET /solrIndex/v1/{uuid}`) and
/// reads it into the editor ([DocEditors#open]); Save sends it to `PUT /solrIndex/v1/{uuid}`.
/// React's `SolrIndexApi` seam is Stroom's `SolrIndexResource`: `fetchSolrTypes` →
/// `POST /solrIndex/v1/fetchSolrTypes`, `connectionTest` → `POST /solrIndex/v1/solrConnectionTest`.
/// React's `recorded` documents (each change) are the document GWT writes when it is saved.
public final class SolrIndexEditorStories {

    private static final DocRef DOC_REF = new DocRef(SolrIndexDoc.TYPE, "solr-events", "Events (Solr)");

    // SolrIndexResource.fetch(): React's INITIAL_DOC
    private static final String DOC = """
            {"type": "SolrIndex", "uuid": "solr-events", "name": "Events (Solr)",
              "description": "# Solr events index", "collection": "events",
              "solrConnectionConfig": {"instanceType": "SINGLE_NOOE", "solrUrls": ["http://localhost:8983/solr"]},
              "fields": FIELDS,
              "solrSynchState": {"lastSynchronized": 1700000000000,
                "messages": ["Synchronised 2 fields from Solr."]}}""";

    private static final String FIELDS = """
            [{"fldName": "EventTime", "fldType": "DATE", "nativeType": "pdate", "indexed": true, "stored": true},
              {"fldName": "UserId", "fldType": "TEXT", "nativeType": "string", "indexed": true, "stored": true,
                "multiValued": false}]""";

    private static final String SOLR_TYPES = """
            ["string", "pdate", "pint", "plong", "boolean", "text_general"]""";

    private static final String UPDATE = "/solrIndex/v1/solr-events";

    // Differs from React: a FormGroup gives its control the group's identity as its id
    // (SolrIndexFieldEditViewImpl.ui.xml), not React's '<name>-input'
    private static final String FIELD_NAME = "#solrIndexFieldName";

    private SolrIndexEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Index/SolrIndexEditor", SolrIndexEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, FIELDS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    for (final String label : new String[]{"Fields", "Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
                    play.expect(play.getByText("UserId")).toBeInTheDocument();
                    // The Solr schema synchronisation state is under the grid
                    play.expect(play.getByText("Last synchronised:")).toBeInTheDocument();
                    // Differs from React: the state is one HTML block, the message a text node in it
                    play.expect(play.getByText(TextMatch.containing("Synchronised 2 fields from Solr.")))
                            .toBeInTheDocument();
                    // Editing a field shows the field dialog, with the fetched native types
                    play.click(play.getByText("EventTime"));
                    play.click(play.getByRole("button", "Edit Field"));
                    play.waitFor(() -> play.expect(screen.getByText("Default Value", "label")).toBeInTheDocument());
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Default Value", "label")).toBeNull());
                    // Settings: the connection, the main time field, retention and Test
                    play.click(DocEditors.tab(play, "Settings"));
                    play.expect(play.findByText("Collection", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Main Time Field Name", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Data Retention Expression (matching data will be deleted)", "label"))
                            .toBeInTheDocument();
                    play.expect(play.getByRole("button", StroomDom.button("Test Connection"))).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // deletedFields is derived from the loaded document when it is written: renaming a
                // loaded field flags its old name; adding then removing a new field doesn't
                .story("DeletedFieldsDerivation", context -> render(context, FIELDS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());

                    // Rename EventTime to EventTimestamp
                    play.click(play.getByText("EventTime"));
                    play.click(play.getByRole("button", "Edit Field"));
                    play.waitFor(() -> play.expect(screen.getByText("Field Name", "label")).toBeInTheDocument());
                    final Query name = screen.querySelector(FIELD_NAME);
                    play.clear(name);
                    play.type(name, "EventTimestamp");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.getByText("EventTimestamp")).toBeInTheDocument());

                    // Add a new field, then remove it
                    play.click(play.getByRole("button", "New Field"));
                    play.waitFor(() -> play.expect(screen.getByText("Field Name", "label")).toBeInTheDocument());
                    play.type(screen.querySelector(FIELD_NAME), "TempField");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.getByText("TempField")).toBeInTheDocument());
                    play.click(play.getByText("TempField"));
                    play.click(play.getByRole("button", "Remove Field"));
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("TempField")).toBeNull());

                    // Differs from React: React records the document on each change; GWT writes
                    // it when it is saved, so the play saves it and checks what was sent
                    final Query save = play.getByRole("button", "Save");
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(UPDATE)
                                    .withBody("fields EventTimestamp and UserId, deleted fields EventTime",
                                            body -> names(body, "fields").equals(List.of("EventTimestamp", "UserId"))
                                                    && names(body, "deletedFields").equals(List.of("EventTime")))
                                    .toSpyMatcher()));
                    DocEditors.expectNoProblems(play);
                })
                // 150 fields: more than one page (MyDataGrid's default page size is 100), and the
                // selection on page 2 edits the right field
                .story("FieldGridPaging", context -> render(context, manyFields()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(play.findByText("Field000")).toBeInTheDocument();
                    play.expect(play.queryByText("Field100")).toBeNull();
                    // Differs from React: Stroom's link tabs only show the selected tab, so the
                    // fields grid's pager is the only one, found by its button's title
                    play.click(play.getByTitle("Forward"));
                    play.waitFor(() -> play.expect(play.getByText("Field100")).toBeInTheDocument());
                    play.expect(play.queryByText("Field000")).toBeNull();
                    play.click(play.getByText("Field100"));
                    play.click(play.getByRole("button", "Edit Field"));
                    play.waitFor(() -> play.expect(screen.querySelector(FIELD_NAME)).toHaveValue("Field100"));
                    DocEditors.expectNoProblems(play);
                })
                // The Fields tab (the default) as an editable partner for FieldsReadOnly
                .story("FieldsEditable", context -> render(context, FIELDS, false))
                .withPlay(play -> {
                    waitForFields(play);
                    DocEditors.expectNoProblems(play);
                })
                // The Fields tab (the default) when the user may only view the index
                .story("FieldsReadOnly", context -> render(context, FIELDS, true))
                .withPlay(play -> {
                    waitForFields(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab as an editable partner for SettingsReadOnly
                .story("SettingsEditable", context -> render(context, FIELDS, false))
                .withPlay(play -> {
                    openSettings(play);
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab when the user may only view the index
                .story("SettingsReadOnly", context -> render(context, FIELDS, true))
                .withPlay(play -> {
                    openSettings(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // Waits for the Fields tab's fields and synchronisation state
    private static void waitForFields(final Play play) {
        play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
        play.expect(play.getByText("Last synchronised:")).toBeInTheDocument();
    }

    // Opens the Settings tab and waits for its form
    private static void openSettings(final Play play) {
        play.waitFor(() -> play.expect(DocEditors.tab(play, "Settings")).toBeInTheDocument());
        play.click(DocEditors.tab(play, "Settings"));
        play.waitFor(() -> play.expect(play.getByText("Collection", "label")).toBeInTheDocument());
        play.expect(play.getByText("Main Time Field Name", "label")).toBeInTheDocument();
    }

    // The names of the fields in a list of a JSON document
    @SuppressWarnings("unchecked")
    private static List<String> names(final String json, final String key) {
        final List<String> names = new ArrayList<>();
        final Object doc = JsonValues.parse(json);
        if (doc instanceof final Map<?, ?> map && map.get(key) instanceof final List<?> fields) {
            for (final Object field : fields) {
                names.add(String.valueOf(((Map<String, Object>) field).get("fldName")));
            }
        }
        return names;
    }

    // 150 text fields, Field000 to Field149
    private static String manyFields() {
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 150; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final String number = String.valueOf(i);
            final String name = "Field" + "000".substring(number.length()) + number;
            sb.append("{\"fldName\": \"").append(name)
                    .append("\", \"fldType\": \"TEXT\", \"nativeType\": \"string\", \"indexed\": true, ")
                    .append("\"stored\": true}");
        }
        return sb.append("]").toString();
    }

    private static Widget render(final StoryContext context, final String fields) {
        return render(context, fields, false);
    }

    private static Widget render(final StoryContext context, final String fields, final boolean readOnly) {
        final RestFixtures fixtures = DocEditors.permissionRoutes(RestFixtures.builder())
                .get(UPDATE, RestReply.json(DOC.replace("FIELDS", fields)))
                .put(UPDATE, request -> RestReply.json(request.getBody()))
                .post("/solrIndex/v1/fetchSolrTypes", RestReply.json(SOLR_TYPES))
                .post("/solrIndex/v1/solrConnectionTest", RestReply.json(
                        "{\"ok\": true, \"message\": \"Solr reachable.\"}"))
                .build();
        final SolrIndexResource resource = GWT.create(SolrIndexResource.class);
        return DocEditors.render(context, fixtures, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getSolrIndexPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
