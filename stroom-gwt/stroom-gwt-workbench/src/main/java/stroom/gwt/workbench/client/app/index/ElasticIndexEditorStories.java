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
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.search.elastic.client.component.ElasticIndexEditor;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The `App/Index/ElasticIndexEditor` stories, showing Stroom's real [ElasticIndexEditor] (an
/// Elastic index's tab: Settings, Fields, Documentation and Permissions) with fake REST replies.
///
/// As `ElasticIndexPlugin` does, the story fetches the document (`GET /elasticIndex/v1/{uuid}`) and
/// reads it into the editor ([DocEditors#open]). Testing the index is
/// `POST /elasticIndex/v1/testIndex`, and the Permissions tab has its own routes.
///
/// The Fields tab's index has a field of each kind: a date, a keyword field whose values can be
/// listed (with a query option), a text field with a keyword multi-field (disabled, by its
/// settings), a `dense_vector` field, a virtual field (`Severity`, with one group), and a field of
/// each problem: one the source indices map to different types (`Status`), settings for a field
/// no index maps (`Gone`), and a virtual field hidden by the mapped field of the same name (`Host`).
/// A field's statistics are `POST /elasticIndex/v1/fieldStats`, and reading the fields again from
/// the mapping is `POST /elasticIndex/v1/fields`.
public final class ElasticIndexEditorStories {

    private static final DocRef DOC_REF = new DocRef(ElasticIndexDoc.TYPE, "el-events", "Events (Elastic)");

    // ElasticIndexResource.fetch(): the index
    private static final String DOC = """
            {"type": "ElasticIndex", "uuid": "el-events", "name": "Events (Elastic)",
              "description": "# Elastic events index",
              "clusterRef": {"type": "ElasticCluster", "uuid": "c1", "name": "Prod Cluster"},
              "indexName": "stroom-events-*", "searchScrollSize": 1000, "searchSlices": 1,
              "fields": [
                {"fldName": "EventTime", "fldType": "DATE", "nativeType": "date", "indexed": true},
                {"fldName": "UserId", "fldType": "KEYWORD", "nativeType": "keyword", "indexed": true,
                  "supportsTopValues": true, "sourceIndices": ["stroom-events-1", "stroom-events-2"],
                  "queryOptionSet": {"definitions": [{"id": "caseInsensitive", "displayName": "Case-insensitive",
                    "type": "BOOLEAN", "defaultValue": "false"}]}},
                {"fldName": "Host", "fldType": "KEYWORD", "nativeType": "keyword", "indexed": true},
                {"fldName": "Status", "fldType": "KEYWORD", "nativeType": "keyword", "indexed": true,
                  "conflictingTypes": {"stroom-events-1": "keyword", "stroom-events-2": "long"}},
                {"fldName": "Message", "fldType": "TEXT", "nativeType": "text", "indexed": true,
                  "mappingParameters": {"analyzer": "english"}},
                {"fldName": "Message.keyword", "fldType": "KEYWORD", "nativeType": "keyword", "indexed": true,
                  "multiFieldParent": "Message"},
                {"fldName": "Embedding", "fldType": "DENSE_VECTOR", "nativeType": "dense_vector",
                  "indexed": true}],
              "fieldSettings": [
                {"fieldName": "Severity", "virtualGroups": [
                  {"name": "High", "expression": {"type": "operator", "op": "AND"}}]},
                {"fieldName": "Message", "enabled": false},
                {"fieldName": "Gone", "enabled": false},
                {"fieldName": "Host", "virtualGroups": [
                  {"name": "Local", "expression": {"type": "operator", "op": "AND"}}]}]}""";

    // No routes of a story's own
    private static final Consumer<RestFixtures.Builder> NO_ROUTES = routes -> {
    };

    private static final String FIELD_STATS_PATH = "/elasticIndex/v1/fieldStats";

    // ElasticIndexResource.fetchFieldStats(): a random sample's statistics
    private static final String FIELD_STATS = """
            {"fieldName": "UserId", "totalDocs": 1000, "docsWithValue": 900, "exact": false,
              "distinctCount": 3, "distinctCountExact": false, "sampleSize": 5000, "sampledDocs": 5000,
              "maxSampleSize": 10000,
              "topValues": [{"value": "admin", "count": 500}, {"value": "bob", "count": 300}]}""";

    // The stories' routes: a story's own first, which win over the rest, and the field statistics'
    // replies (the last repeating)
    private static RestFixtures fixtures(final Consumer<RestFixtures.Builder> routes,
                                         final RestReply stats,
                                         final RestReply... moreStats) {
        final RestFixtures.Builder builder = RestFixtures.builder();
        routes.accept(builder);
        builder.route(RequestMatcher.post(FIELD_STATS_PATH), stats, moreStats);
        return DocEditors.docSelectionRoutes(
                        DocEditors.permissionRoutes(builder))
            .get("/elasticIndex/v1/el-events", RestReply.json(DOC))
            .put("/elasticIndex/v1/el-events", request -> RestReply.json(request.getBody()))
            .post("/elasticIndex/v1/testIndex", RestReply.json(
                    "{\"ok\": true, \"message\": \"Connected to 3 nodes.\"}"))
            .build();
    }

    private ElasticIndexEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Index/ElasticIndexEditor", ElasticIndexEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Settings (default), Fields (read only), Documentation and Permissions
                .story("Default", ElasticIndexEditorStories::render)
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Fields", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // Settings is the default tab, grouped under <h2> headings
                    play.waitFor(() -> play.expect(play.getByRole("heading", "General settings"))
                            .toBeInTheDocument());
                    play.expect(play.getByRole("heading", "Search settings")).toBeInTheDocument();
                    play.expect(play.getByText("Index name or pattern", "label")).toBeInTheDocument();
                    play.expect(play.getByRole("button", StroomDom.button("Test Connection"))).toBeInTheDocument();
                    // Fields lists the mapping's fields
                    play.click(DocEditors.tab(play, "Fields"));
                    play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
                    play.expect(play.getByText("UserId")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab (the default) as an editable partner for SettingsReadOnly
                .story("SettingsEditable", context -> render(context, false))
                .withPlay(play -> {
                    waitForSettings(play);
                    DocEditors.expectNoProblems(play);
                })
                // Changing a setting makes the index changed, so Save is enabled
                .story("SettingChange", context -> render(context, false))
                .withPlay(play -> {
                    waitForSettings(play);
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.type(play.getByLabelText("Main date field"), "_changed");
                    // The text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // The Settings tab (the default) when the user may only view the index
                .story("SettingsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    waitForSettings(play);
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    play.expect(play.getByLabelText("Index name or pattern")).toHaveAttribute("readonly");
                    play.expect(play.getByLabelText("Main date field")).toHaveAttribute("readonly");
                    DocEditors.expectNoProblems(play);
                })
                // The Fields tab as an editable partner for FieldsReadOnly (the fields come from the
                // index's mapping, so are never edited here)
                .story("FieldsEditable", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    DocEditors.expectNoProblems(play);
                })
                // The Fields tab when the user may only view the index
                .story("FieldsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    openFields(play);
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    play.expect(play.getByRole("button", "Add Virtual Field")).toHaveAttribute("aria-disabled", "true");
                    // A virtual field's groups can still be viewed
                    selectField(play, "Severity");
                    final Play settings = openFieldSettings(play, "Severity");
                    play.expect(settings.getByRole("button", "View Selected Group")).toBeInTheDocument();
                    play.expect(settings.getByRole("button", "Add Group")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // Selecting a field shows how it is mapped and its statistics, from a random sample
                .story("FieldInfo", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "UserId");
                    play.waitFor(() -> play.expect(play.getByText("Native type")).toBeInTheDocument());
                    play.expect(play.getByText("stroom-events-2")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.getByText("Documents with a value")).toBeInTheDocument());
                    play.expect(play.getByText("900 (90%)")).toBeInTheDocument();
                    play.expect(play.getByText("admin")).toBeInTheDocument();
                    scrollIntoView(play, "bob");
                    DocEditors.expectNoProblems(play);
                })
                // A field's multi-fields are under it, shown when it is expanded
                .story("MultiFields", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    play.expect(play.queryByText("Message.keyword", StroomDom.GRID_CELL_TEXT)).toBeNull();
                    play.click(play.getByRole("button", "Expand All"));
                    play.waitFor(() -> play.expect(play.getByText("Message.keyword", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // Unticking a field's Enabled in its Settings panel makes the index changed
                .story("FieldEnabledChange", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    selectField(play, "UserId");
                    final Play settings = openFieldSettings(play, "UserId");
                    play.click(settings.getByRole("checkbox", "Enabled"));
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // Add Virtual Field asks for a name; OK adds and selects it, showing its settings
                .story("AddVirtualFieldOk", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    final Play dialog = openDialog(play, "Add Virtual Field", "New Virtual Field");
                    play.type(dialog.getByRole("textbox", "Name"), "Level");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    expectDialogClosed(play, "New Virtual Field");
                    play.waitFor(() -> play.expect(play.getByText("Level", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument());
                    play.expect(play.getByRole("heading", "Level")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Cancel adds nothing
                .story("AddVirtualFieldCancel", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    final Play dialog = openDialog(play, "Add Virtual Field", "New Virtual Field");
                    play.type(dialog.getByRole("textbox", "Name"), "Level");
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectDialogClosed(play, "New Virtual Field");
                    play.expect(play.queryByText("Level", StroomDom.GRID_CELL_TEXT)).toBeNull();
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // A virtual field can't take a mapped field's name: OK refuses and says why
                .story("VirtualFieldNameRefused", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    final Play dialog = openDialog(play, "Add Virtual Field", "New Virtual Field");
                    play.type(dialog.getByRole("textbox", "Name"), "UserId");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(dialog.getByText("Name clashes with a mapped field."))
                            .toBeInTheDocument());
                    play.expect(dialog.getByRole("textbox", "Name")).toHaveValue("UserId");
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectDialogClosed(play, "New Virtual Field");
                    DocEditors.expectNoProblems(play);
                })
                // A virtual field's Add Group asks for a name and query; OK adds it to the field's groups
                .story("AddGroupOk", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    final Play settings = openFieldSettings(play, "Severity");
                    play.expect(settings.getByText("High", StroomDom.GRID_CELL_TEXT)).toBeInTheDocument();
                    play.click(settings.getByRole("button", "Add Group"));
                    final Play dialog = dialog(play, "Add Group");
                    play.type(dialog.getByRole("textbox", "Name"), "Low");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    expectDialogClosed(play, "Add Group");
                    play.waitFor(() -> play.expect(settings.getByText("Low", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument());
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Cancel leaves the groups as they were
                .story("AddGroupCancel", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    final Play settings = openFieldSettings(play, "Severity");
                    play.click(settings.getByRole("button", "Add Group"));
                    final Play dialog = dialog(play, "Add Group");
                    play.type(dialog.getByRole("textbox", "Name"), "Low");
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectDialogClosed(play, "Add Group");
                    play.expect(settings.queryByText("Low", StroomDom.GRID_CELL_TEXT)).toBeNull();
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // A dense_vector field's minimum rerank score that isn't a score says why under it,
                // and Save takes the user to it rather than saving (it was the index's own setting)
                .story("RerankScoreNotAScore", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Embedding");
                    final Play settings = openFieldSettings(play, "Embedding");
                    final Query score = settings.getByLabelText("Minimum rerank score");
                    play.clear(score);
                    play.type(score, "high");
                    play.tab();
                    play.waitFor(() -> play.expect(settings.getByText("Must be a number from 0 to 1"))
                            .toBeInTheDocument());
                    final Query save = play.getByRole("button", "Save");
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(score).toHaveFocus());
                    play.expect(score).toHaveValue("high");
                    DocEditors.expectNoProblems(play);
                })
                // The type filter's chooser shows only the fields of the types ticked
                .story("TypeFilter", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    play.click(play.getByRole("button", TextMatch.startingWith("Field types")));
                    play.click(play.screen().findByRole("option", "Date"));
                    play.waitFor(() -> play.expect(play.queryByText("UserId", StroomDom.GRID_CELL_TEXT)).toBeNull());
                    play.expect(play.getByText("EventTime", StroomDom.GRID_CELL_TEXT)).toBeInTheDocument();
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.getByRole("button", TextMatch.startingWith("Field types")))
                            .toHaveTextContent(TextMatch.containing("Date")));
                    DocEditors.expectNoProblems(play);
                })
                // Fields that can't be used as normal are faded, with why in Status
                .story("ProblemFields", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    play.expect(play.getByText(TextMatch.startingWith("Type conflict"), StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument();
                    play.expect(play.getByText("Missing from all source indices", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument();
                    play.expect(play.getByText("Hidden by a mapped field of the same name", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument();
                    // A missing field's Info says why it has no statistics
                    selectField(play, "Gone");
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.startingWith("No source index maps")))
                            .toBeInTheDocument());
                    DocEditors.expectNoProblems(play);
                })
                // Rename asks for the virtual field's new name; OK renames it
                .story("RenameVirtualField", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    final Play dialog = openDialog(play, "Rename Selected Virtual Field", "Rename Virtual Field");
                    final Query name = dialog.getByRole("textbox", "Name");
                    play.expect(name).toHaveValue("Severity");
                    play.clear(name);
                    play.type(name, "Level");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    expectDialogClosed(play, "Rename Virtual Field");
                    play.waitFor(() -> play.expect(play.getByText("Level", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument());
                    play.expect(play.queryByText("Severity", StroomDom.GRID_CELL_TEXT)).toBeNull();
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // A double click on a virtual field renames it too
                .story("RenameByDoubleClick", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    play.dblClick(play.findByText("Severity", StroomDom.GRID_CELL_TEXT));
                    final Play dialog = dialog(play, "Rename Virtual Field");
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    expectDialogClosed(play, "Rename Virtual Field");
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // Delete asks first; OK deletes the virtual field
                .story("DeleteVirtualField", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    play.click(play.getByRole("button", "Delete Selected Virtual Field"));
                    final Play screen = play.screen();
                    final Play confirm = screen.within(screen.findByText(
                            "Are you sure you want to delete the virtual field 'Severity'?").closest(StroomDom.DIALOG));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("Severity", StroomDom.GRID_CELL_TEXT)).toBeNull());
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Remove takes a mapped field's settings away, so it is enabled again
                .story("RemoveSettings", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Message");
                    final Play settings = openFieldSettings(play, "Message");
                    play.expect(settings.getByRole("checkbox", "Enabled")).not().toBeChecked();
                    play.click(play.getByRole("button", "Remove Settings Of Selected Field"));
                    play.waitFor(() -> play.expect(settings.getByRole("checkbox", "Enabled")).toBeChecked());
                    play.expect(play.getByRole("button", "Remove Settings Of Selected Field"))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Refresh reads the fields again from the index's mapping
                .story("RefreshFromMapping", context -> render(context, false, routes -> routes.post(
                        "/elasticIndex/v1/fields", RestReply.json("""
                                [{"fldName": "EventTime", "fldType": "DATE", "nativeType": "date", "indexed": true},
                                  {"fldName": "Uri", "fldType": "KEYWORD", "nativeType": "keyword",
                                    "indexed": true}]"""))))
                .withPlay(play -> {
                    openFields(play);
                    play.click(play.getByRole("button", "Refresh Fields From Index Mapping"));
                    play.waitFor(() -> play.expect(play.getByText("Uri", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument());
                    // UserId has gone from the mapping, as it has no settings to keep it
                    play.expect(play.queryByText("UserId", StroomDom.GRID_CELL_TEXT)).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/elasticIndex/v1/fields")
                                    .withJsonBodyContaining("{\"indexName\": \"stroom-events-*\"}")
                                    .toSpyMatcher());
                    DocEditors.expectNoProblems(play);
                })
                // Edit changes a group's name in its dialog
                .story("EditGroup", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    final Play settings = openFieldSettings(play, "Severity");
                    play.click(settings.getByRole("button", "Edit Selected Group"));
                    final Play dialog = dialog(play, "Edit Group");
                    final Query name = dialog.getByRole("textbox", "Name");
                    play.expect(name).toHaveValue("High");
                    play.clear(name);
                    play.type(name, "Critical");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    expectDialogClosed(play, "Edit Group");
                    play.waitFor(() -> play.expect(settings.getByText("Critical", StroomDom.GRID_CELL_TEXT))
                            .toBeInTheDocument());
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Remove takes the selected group away
                .story("RemoveGroup", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    final Play settings = openFieldSettings(play, "Severity");
                    play.click(settings.getByRole("button", "Remove Selected Group"));
                    play.waitFor(() -> play.expect(settings.queryByText("High", StroomDom.GRID_CELL_TEXT)).toBeNull());
                    play.expect(play.getByRole("button", "Save")).not().toHaveAttribute("aria-disabled");
                    DocEditors.expectNoProblems(play);
                })
                // A read-only index's group opens to be looked at, with Close
                .story("ViewGroupReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    final Play settings = openFieldSettings(play, "Severity");
                    play.click(settings.getByRole("button", "View Selected Group"));
                    final Play dialog = dialog(play, "View Group");
                    play.expect(dialog.getByRole("textbox", "Name")).toHaveAttribute("readonly");
                    play.click(dialog.getByRole("button", StroomDom.button("Close")));
                    expectDialogClosed(play, "View Group");
                    DocEditors.expectNoProblems(play);
                })
                // Left open, to look at: a new group's query, whose terms may use the index's keyword
                // fields only, each with its query options and value picker, as a dashboard's term has
                .story("GroupTermFields", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Severity");
                    final Play settings = openFieldSettings(play, "Severity");
                    play.click(settings.getByRole("button", "Add Group"));
                    final Play dialog = dialog(play, "Add Group");
                    play.type(dialog.getByRole("textbox", "Name"), "Low");
                    play.click(dialog.getByRole("button", "Add Term"));
                    // A new term is edited at once
                    play.waitFor(() -> play.expect(dialog.querySelector(".term-editor__item--field")).not().toBeNull());
                    play.click(dialog.querySelector(".term-editor__item--field .selection-box__input"));
                    final Play screen = play.screen();
                    play.expect(screen.findByRole("option", "Message.keyword")).toBeInTheDocument();
                    play.expect(screen.queryByRole("option", "EventTime")).toBeNull();
                    play.expect(screen.queryByRole("option", "Message")).toBeNull();
                    play.click(screen.getByRole("option", "UserId"));
                    play.waitFor(() -> play.expect(dialog.getByRole("button", "Pick Value")).toBeInTheDocument());
                    play.expect(dialog.getByRole("button", "Query Options")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // A dense_vector field's vector search settings; a change makes the index changed
                .story("DenseVectorSettings", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "Embedding");
                    final Play settings = openFieldSettings(play, "Embedding");
                    play.expect(settings.getByRole("heading", "Vector search")).toBeInTheDocument();
                    play.expect(settings.getByLabelText("Embedding model")).toBeInTheDocument();
                    // No rerank text field is chosen: the box shows its list's None item
                    play.expect(settings.getByLabelText("Rerank text field")).toHaveValue("None");
                    final Query count = settings.getByRole("spinbutton", "Nearest neighbour count");
                    play.clear(count);
                    play.type(count, "5");
                    play.tab();
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save")).not()
                            .toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // Exact statistics are for a time range, and ask for exact counts
                .story("InfoExactMode", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "UserId");
                    play.waitFor(() -> play.expect(play.getByText("Documents with a value")).toBeInTheDocument());
                    play.click(play.getByLabelText("Mode"));
                    play.click(play.screen().findByRole("option", "Exact (all documents)"));
                    play.waitFor(() -> play.expect(play.getByLabelText("From")).toBeVisible());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIELD_STATS_PATH).withJsonBodyContaining("{\"exact\": true}")
                                    .toSpyMatcher()));
                    scrollIntoView(play, "bob");
                    DocEditors.expectNoProblems(play);
                })
                // The most common values' filter and number fetch only those values again
                .story("TopValuesFilter", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "UserId");
                    play.waitFor(() -> play.expect(play.getByText("admin")).toBeInTheDocument());
                    play.type(play.getByRole("searchbox", "Filter values"), "adm");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIELD_STATS_PATH)
                                    .withJsonBodyContaining("{\"topValuesOnly\": true, \"topValuesFilter\": \"adm\"}")
                                    .toSpyMatcher()));
                    play.click(play.getByLabelText("Number of values"));
                    play.click(play.screen().findByRole("option", "Top 25"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIELD_STATS_PATH)
                                    .withJsonBodyContaining("{\"topValuesOnly\": true, \"topValuesCount\": 25}")
                                    .toSpyMatcher()));
                    scrollIntoView(play, "bob");
                    DocEditors.expectNoProblems(play);
                })
                // Statistics that couldn't be fetched say why, with Retry, which fetches them again
                .story("StatsErrorRetry", context -> render(context, false, NO_ROUTES,
                        RestReply.error(500, "Elasticsearch unavailable"),
                        RestReply.json(FIELD_STATS)))
                .withPlay(play -> {
                    openFields(play);
                    selectField(play, "UserId");
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.startingWith("Unable to fetch statistics")))
                            .toBeInTheDocument());
                    play.click(play.getByRole("button", StroomDom.button("Retry")));
                    play.waitFor(() -> play.expect(play.getByText("Documents with a value")).toBeInTheDocument());
                    scrollIntoView(play, "bob");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // Left open, to look at: the new virtual field's dialog, with a name typed
                .story("AddVirtualFieldOpen", context -> render(context, false))
                .withPlay(play -> {
                    openFields(play);
                    final Play dialog = openDialog(play, "Add Virtual Field", "New Virtual Field");
                    play.type(dialog.getByRole("textbox", "Name"), "Level");
                    DocEditors.expectNoProblems(play);
                });
    }

    // Scrolls the text into view, so the story's picture shows it (the Info panel is longer than its space)
    private static void scrollIntoView(final Play play, final String text) {
        final Query target = play.findByText(text);
        play.run("scroll '" + text + "' into view", () -> target.element().get().scrollIntoView());
    }

    // Selects a field in the Fields tab's list, by its name
    private static void selectField(final Play play, final String name) {
        play.click(play.findByText(name, StroomDom.GRID_CELL_TEXT));
    }

    // Shows the selected field's Settings panel (the field details' tab, not the index's), which its
    // name heads, returning it
    private static Play openFieldSettings(final Play play, final String fieldName) {
        play.click(play.within(play.findByRole("tablist", "Field details")).findByRole("tab", "Settings"));
        return play.within(play.findByRole("heading", fieldName).closest(".elastic-field-settings"));
    }

    // Presses a button that opens a dialog, returning the dialog
    private static Play openDialog(final Play play, final String button, final String caption) {
        play.click(play.getByRole("button", button));
        return dialog(play, caption);
    }

    private static Play dialog(final Play play, final String caption) {
        final Play screen = play.screen();
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    private static void expectDialogClosed(final Play play, final String caption) {
        play.waitFor(() -> play.expect(play.screen().queryByText(caption, StroomDom.DIALOG_TITLE)).toBeNull());
    }

    // Waits for the Settings tab's form
    private static void waitForSettings(final Play play) {
        play.waitFor(() -> play.expect(play.getByRole("heading", "General settings")).toBeInTheDocument());
        play.expect(play.getByText("Index name or pattern", "label")).toBeInTheDocument();
    }

    // Opens the Fields tab and waits for its fields
    private static void openFields(final Play play) {
        play.waitFor(() -> play.expect(DocEditors.tab(play, "Fields")).toBeInTheDocument());
        play.click(DocEditors.tab(play, "Fields"));
        play.waitFor(() -> play.expect(play.getByText("EventTime")).toBeInTheDocument());
    }

    private static Widget render(final StoryContext context) {
        return render(context, false);
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        return render(context, readOnly, NO_ROUTES);
    }

    private static Widget render(final StoryContext context,
                                 final boolean readOnly,
                                 final Consumer<RestFixtures.Builder> routes) {
        return render(context, readOnly, routes, RestReply.json(FIELD_STATS));
    }

    private static Widget render(final StoryContext context,
                                 final boolean readOnly,
                                 final Consumer<RestFixtures.Builder> routes,
                                 final RestReply stats,
                                 final RestReply... moreStats) {
        final ElasticIndexResource resource = GWT.create(ElasticIndexResource.class);
        final RestFixtures fixtures = fixtures(routes, stats, moreStats);
        return DocEditors.render(context, fixtures, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getElasticIndexEditor(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
