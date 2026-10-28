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
import stroom.entity.client.component.DocumentEditorHost;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.planb.client.component.PlanBEditor;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocResource;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The `App/Editors/PlanBEditor` stories, showing Stroom's real `PlanBEditor` (a Plan B store's
/// editor tab: Settings, Documentation and Permissions) with fake REST replies.
///
/// As `PlanBPlugin` does, the story fetches the document (`GET /planB/v1/{uuid}`), checks the user
/// may edit it and reads it into the editor. The Permissions tab asks
/// `POST /permission/doc/v1/fetchDocumentUserPermissions`. The settings' types are Stroom's JSON
/// type names (`state`, `metric`, `session`, `trace`).
public final class PlanBEditorStories {

    // The caption of a settings group (SettingsGroup's FormLabel).
    // The groups' captions are labels, not headings
    private static final String GROUP_LABEL = "label.settings-group__label";
    private static final String GROUP = ".settings-group";
    private static final String FORM_GROUP = ".form-group";

    // PlanBDocResource.fetch(): a store of a type with its settings
    private static final String DOC = """
            {
              "type": "PlanB", "uuid": "planb-STATE_TYPE", "name": "My STATE_TYPE",
              "stateType": "STATE_TYPE",
              "settings": {"type": "SETTINGS_TYPE"MAX_STORE_SIZEKEY_SCHEMA},
              "description": "# Plan B docs"
            }""";

    private static final String MAX_STORE_SIZE = ", \"maxStoreSize\": 1073741824";

    private static final RestFixtures FIXTURES = DocumentEditors.ownerPermissions(RestFixtures.builder())
            .get("/planB/v1/planb-STATE", RestReply.json(doc("STATE", "state", MAX_STORE_SIZE, "")))
            .get("/planB/v1/planb-METRIC", RestReply.json(doc("METRIC", "metric", MAX_STORE_SIZE, "")))
            .get("/planB/v1/planb-SESSION", RestReply.json(doc("SESSION", "session", MAX_STORE_SIZE, "")))
            .get("/planB/v1/planb-TRACE", RestReply.json(doc("TRACE", "trace", MAX_STORE_SIZE, "")))
            .get("/planB/v1/planb-TEMPORAL_STATE", RestReply.json(doc("TEMPORAL_STATE", "temporalState",
                    MAX_STORE_SIZE, "")))
            .get("/planB/v1/planb-HISTOGRAM", RestReply.json(doc("HISTOGRAM", "histogram", MAX_STORE_SIZE, "")))
            // MetricTimeZoneOffset: the key schema's time zone is an offset
            .get("/planB/v1/planb-METRIC-OFFSET", RestReply.json(doc("METRIC", "metric", MAX_STORE_SIZE,
                    ", \"keySchema\": {\"timeZone\": {\"use\": \"OFFSET\", \"offsetHours\": -1, "
                            + "\"offsetMinutes\": 30}}")
                    .replace("planb-METRIC", "planb-METRIC-OFFSET")))
            .put("/planB/v1/planb-METRIC-OFFSET", request -> RestReply.json(request.getBody()))
            // MaxStoreSizeDefaultsWhenUnset: no maxStoreSize
            .get("/planB/v1/planb-STATE-UNSET", RestReply.json(doc("STATE", "state", "", "")
                    .replace("planb-STATE", "planb-STATE-UNSET")))
            .build();

    private PlanBEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/PlanBEditor", PlanBEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // STATE: General, Snapshot, Data Retention, Key Schema and Value Schema
                .story("State", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocumentEditors.tab(play, label)).toBeInTheDocument());
                    }
                    for (final String group : new String[]{
                            "General", "Snapshot", "Data Retention", "Key Schema", "Value Schema"}) {
                        play.waitFor(() -> play.expect(play.getByText(group, GROUP_LABEL)).toBeInTheDocument());
                    }
                    play.expect(play.getByText("Key Type", "label")).toBeInTheDocument();
                    play.expect(play.getByText("State Value Type", "label")).toBeInTheDocument();
                    // maxStoreSize is shown as an IEC byte size; GWT strips the ".0"
                    play.expect(maxStoreSize(play)).toHaveValue("1G");
                    // Retain For is a group of an amount and its unit, each named
                    final Play retainFor = play.within(play.getByRole("group", "Retain For"));
                    play.expect(retainFor.getByRole("spinbutton", "Amount")).toBeInTheDocument();
                    play.expect(retainFor.getByRole("combobox", "Unit")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocumentEditors.expectNoProblems(play);
                })
                // METRIC: the value schema is a Value Size and the store-aggregate tick boxes, and
                // the key schema has a Temporal Resolution and a Time Zone
                .story("Metric", context -> render(context, "planb-METRIC", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Temporal Resolution", "label"))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Value Size", "label")).toBeInTheDocument();
                    // Tick boxes, each named by its own label
                    for (final String label : new String[]{
                            "Store Latest Value", "Store Min Value", "Store Max Value", "Store Count", "Store Sum"}) {
                        play.expect(play.getByRole("checkbox", label)).toBeInTheDocument();
                    }
                    // The key schema's TimeZoneBox
                    play.expect(play.getByText("Time Zone", "label")).toBeInTheDocument();
                    // A selection box in a form group is as wide as a text field, not the screen (its
                    // width was once lost, so they stretched across it)
                    play.expect(play.getByRole("combobox", "Temporal Resolution").closest(".selection-box"))
                            .toHaveStyle("width", "200px");
                    DocumentEditors.expectNoProblems(play);
                })
                // The Time Zone control shows the offset when Use is Offset (TimeZoneBox)
                .story("MetricTimeZoneOffset", context -> render(context, "planb-METRIC-OFFSET", false))
                .withPlay(play -> {
                    // The offset is whole hours: its minutes can't be set, as browsers don't support
                    // minute offsets, so there is no minutes spinner (it was always hidden)
                    final Query offset = play.findByText("Time Zone Offset", "label");
                    final Play group = play.within(offset.closest(FORM_GROUP));
                    play.expect(group.querySelectorAll("input")).toHaveLength(1);
                    play.expect(group.querySelectorAll("input").nth(0)).toHaveValue("-1");
                    // The hours are named by the label, not only "Hours"
                    play.expect(play.getByRole("spinbutton", "Time Zone Offset")).toHaveValue("-1");
                    DocumentEditors.expectNoProblems(play);
                })
                // An offset's minutes, which can't be set here (there is no minutes spinner), are kept
                // when the time zone is saved
                .story("TimeZoneOffsetKeepsItsMinutes",
                        context -> renderSaving(context, "planb-METRIC-OFFSET"))
                .withPlay(play -> {
                    final Query hours = play.findByRole("spinbutton", "Time Zone Offset");
                    play.waitFor(() -> play.expect(hours).toHaveValue("-1"));
                    play.click(hours);
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(hours).toHaveValue("-2"));
                    final Query save = play.getByRole("button", "Save");
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/planB/v1/planb-METRIC-OFFSET")
                                    .withJsonBodyContaining("{\"settings\": {\"keySchema\": {\"timeZone\": "
                                                            + "{\"offsetHours\": -2, \"offsetMinutes\": 30}}}}")
                                    .toSpyMatcher()));
                    DocumentEditors.expectNoProblems(play);
                })
                // SESSION: has Condense Data and no Value Schema
                .story("Session", context -> render(context, "planb-SESSION", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Condense Data", GROUP_LABEL))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Key Schema", GROUP_LABEL)).toBeInTheDocument();
                    play.expect(play.queryByText("Value Schema", GROUP_LABEL)).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // TRACE: only the common groups, no Key or Value Schema
                .story("Trace", context -> render(context, "planb-TRACE", false))
                .withPlay(play -> {
                    // The trace settings have no 'General' group; their common groups are Shared
                    // File Store, Publishing, Storage and Data Retention
                    play.waitFor(() -> play.expect(play.getByText("Storage", GROUP_LABEL)).toBeInTheDocument());
                    play.expect(play.queryByText("General", GROUP_LABEL)).toBeNull();
                    play.expect(play.getByText("Data Retention", GROUP_LABEL)).toBeInTheDocument();
                    play.expect(play.queryByText("Key Schema", GROUP_LABEL)).toBeNull();
                    play.expect(play.queryByText("Value Schema", GROUP_LABEL)).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // Changing the type shows that type's groups, and keeps the settings every type has:
                // here the document's maximum store size, 1G, where a new type's default is 10G
                .story("ChangingTypeKeepsCommonSettings", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(maxStoreSize(play)).toHaveValue("1G"));
                    for (final String type : List.of("Temporal State", "Range State", "Temporal Range State",
                            "Histogram")) {
                        play.click(stateType(play));
                        play.click(play.screen().findByText(type, ".selection-popup *"));
                        play.waitFor(() -> play.expect(maxStoreSize(play)).toHaveValue("1G"));
                    }
                    // A histogram store's groups: no condense settings
                    play.expect(play.getByText("Key Schema", GROUP_LABEL)).toBeInTheDocument();
                    play.expect(play.queryByText("Condense Data", GROUP_LABEL)).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // The temporal, ranged and histogram types show their own groups
                .story("OtherTypes", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(maxStoreSize(play)).toBeInTheDocument());
                    play.click(stateType(play));
                    play.click(play.screen().findByText("Range State", ".selection-popup *"));
                    play.waitFor(() -> play.expect(play.queryByText("Condense Data", GROUP_LABEL)).toBeNull());
                    play.expect(play.getByText("Value Schema", GROUP_LABEL)).toBeInTheDocument();
                    for (final String type : List.of("Temporal State", "Temporal Range State")) {
                        play.click(stateType(play));
                        play.click(play.screen().findByText(type, ".selection-popup *"));
                        play.waitFor(() -> play.expect(play.getByText("Condense Data", GROUP_LABEL))
                                .toBeInTheDocument());
                        play.expect(play.getByText("Value Schema", GROUP_LABEL)).toBeInTheDocument();
                    }
                    play.click(stateType(play));
                    play.click(play.screen().findByText("Histogram", ".selection-popup *"));
                    play.waitFor(() -> play.expect(play.queryByText("Condense Data", GROUP_LABEL)).toBeNull());
                    play.expect(selection(play, "Key Schema", "Temporal Resolution")).toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                })
                // Changing the type keeps the data retention settings too
                .story("ChangingTypeKeepsRetention", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(retentionEnabled(play)).not().toBeChecked());
                    play.click(retentionEnabled(play));
                    play.waitFor(() -> play.expect(retentionEnabled(play)).toBeChecked());
                    for (final String type : List.of("Temporal State", "Histogram")) {
                        play.click(stateType(play));
                        play.click(play.screen().findByText(type, ".selection-popup *"));
                        play.waitFor(() -> play.expect(retentionEnabled(play)).toBeChecked());
                    }
                    DocumentEditors.expectNoProblems(play);
                })
                // Editing a setting makes the document dirty, so Save is enabled
                .story("EditEnablesSave", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.waitFor(() -> play.expect(maxStoreSize(play)).toBeInTheDocument());
                    play.click(maxStoreSize(play));
                    play.clear(maxStoreSize(play));
                    play.type(maxStoreSize(play), "512 M");
                    // The text box reports its value when it loses the focus (a change event), not
                    // as it is typed
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    DocumentEditors.expectNoProblems(play);
                })
                // Read only: the settings are disabled and Save can never be enabled
                .story("ReadOnly", context -> render(context, "planb-STATE", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(maxStoreSize(play)).toHaveAttribute("readonly"));
                    play.expect(play.getByRole("button", "Save (read only)"))
                            .toHaveAttribute("aria-disabled", "true");
                    DocumentEditors.expectNoProblems(play);
                })
                // The document is locked and unlocked while it is open, as reading it again does
                // when its lock changes: every settings field follows, as they watch READ_ONLY
                // (from the document) rather than being told by the controllers between
                .story("LockChangeWhileOpen", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(maxStoreSize(play)).not().toHaveAttribute("readonly"));
                    play.run("lock the document", () -> readAgain(true));
                    play.expect(maxStoreSize(play)).toHaveAttribute("readonly");
                    play.expect(play.getByRole("button", "Save (read only)")).toBeInTheDocument();
                    play.run("unlock the document", () -> readAgain(false));
                    play.expect(maxStoreSize(play)).not().toHaveAttribute("readonly");
                    play.expect(play.getByRole("button", "Save")).toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                })
                // METRIC read only: the same settings as Metric, when the user may only view the store
                .story("MetricReadOnly", context -> render(context, "planb-METRIC", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Temporal Resolution", "label"))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // SESSION read only: the same settings as Session, when the user may only view the store
                .story("SessionReadOnly", context -> render(context, "planb-SESSION", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Condense Data", GROUP_LABEL))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // TRACE read only: the same settings as Trace, when the user may only view the store
                .story("TraceReadOnly", context -> render(context, "planb-TRACE", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Storage", GROUP_LABEL)).toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // TEMPORAL_STATE read only: its condense settings can't be changed either
                .story("TemporalStateReadOnly", context -> render(context, "planb-TEMPORAL_STATE", true))
                .withPlay(play -> {
                    final Play condense = play.within(play.findByText("Condense Data", GROUP_LABEL).closest(GROUP));
                    play.expect(condense.getByRole("checkbox", "Enabled")).toHaveAttribute("aria-readonly", "true");
                    play.expect(retentionEnabled(play)).toHaveAttribute("aria-readonly", "true");
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // HISTOGRAM read only: its settings can't be changed
                .story("HistogramReadOnly", context -> render(context, "planb-HISTOGRAM", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(selection(play, "Key Schema", "Temporal Resolution"))
                            .toBeInTheDocument());
                    play.expect(maxStoreSize(play)).toHaveAttribute("readonly");
                    play.expect(retentionEnabled(play)).toHaveAttribute("aria-readonly", "true");
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // Unset schema fields show the Java defaults of their schemas
                .story("UnsetSchemaFieldsShowTheirJavaDefaults", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    // The selection box's value is its text box's value; 'Hash Length' is in both
                    // schemas, so it is found within the Key Schema group
                    play.waitFor(() -> play.expect(selection(play, "Key Schema", "Key Type"))
                            .toHaveValue("Variable"));
                    play.expect(selection(play, "Value Schema", "State Value Type")).toHaveValue("Variable");
                    play.expect(selection(play, "Key Schema", "Hash Length")).toHaveValue("Integer");
                    DocumentEditors.expectNoProblems(play);
                })
                // GeneralSettingsForm shows DEFAULT_MAX_STORE_SIZE for an unset max store size
                .story("MaxStoreSizeDefaultsWhenUnset", context -> render(context, "planb-STATE-UNSET", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(maxStoreSize(play)).toHaveValue("10G"));
                    DocumentEditors.expectNoProblems(play);
                });
    }

    private static String doc(final String stateType,
                              final String settingsType,
                              final String maxStoreSize,
                              final String keySchema) {
        return DOC.replace("STATE_TYPE", stateType)
                .replace("SETTINGS_TYPE", settingsType)
                .replace("MAX_STORE_SIZE", maxStoreSize)
                .replace("KEY_SCHEMA", keySchema);
    }

    // The Max Store Size text box.
    // Its FormGroup has no identity, so it has no id
    // Reads the shown document again, as DocumentPlugin does, read only or not
    private static void readAgain(final boolean readOnly) {
        final DocumentEditorHost<PlanBDoc> host = ScreenHarness.shownComponent();
        host.setReadOnly(readOnly);
        host.read(host.getDocRef(), host.getEntity());
    }

    // The State Type selection box's text box
    private static Query stateType(final Play play) {
        return play.within(play.getByText("State Type", "label").closest(FORM_GROUP))
                .querySelector(StroomDom.SELECTION_BOX);
    }

    private static Query maxStoreSize(final Play play) {
        return play.within(play.getByText("Max Store Size", "label").closest(FORM_GROUP)).querySelector("input");
    }

    // The Data Retention group's Enabled tick box
    private static Query retentionEnabled(final Play play) {
        return play.within(play.getByText("Data Retention", GROUP_LABEL).closest(GROUP))
                .getByRole("checkbox", "Enabled");
    }

    // The text box of the selection box labelled `label` in the settings group `group`
    private static Query selection(final Play play, final String group, final String label) {
        final Play groupPlay = play.within(play.getByText(group, GROUP_LABEL).closest(GROUP));
        return groupPlay.within(groupPlay.getByText(label, "label").closest(FORM_GROUP))
                .querySelector(StroomDom.SELECTION_BOX);
    }

    private static Widget render(final StoryContext context, final String uuid, final boolean readOnly) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        final DocRef docRef = new DocRef(PlanBDoc.TYPE, uuid, uuid);
        final PlanBDocResource resource = GWT.create(PlanBDocResource.class);
        // Opened once Stroom has started, as PlanBPlugin opens a document
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getPlanBEditor(), docRef,
                resource, res -> res.fetch(docRef.getUuid())));
        return harness.asWidget();
    }

    // As render, with Save sending the document (PUT /planB/v1/{uuid}), as DocumentPlugin does
    private static Widget renderSaving(final StoryContext context, final String uuid) {
        final QueryScreenGinjector injector = GWT.create(QueryScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.getSecurityContext().setDocumentPermission(DocumentPermission.EDIT);
        final DocRef docRef = new DocRef(PlanBDoc.TYPE, uuid, uuid);
        final PlanBDocResource resource = GWT.create(PlanBDocResource.class);
        harness.afterStartUp(() -> DocEditors.open(harness, docRef, injector.getPlanBEditor(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(uuid)),
                        (restFactory, doc) -> restFactory.create(resource).method(res -> res.update(uuid, doc)))));
        return harness.asWidget();
    }
}
