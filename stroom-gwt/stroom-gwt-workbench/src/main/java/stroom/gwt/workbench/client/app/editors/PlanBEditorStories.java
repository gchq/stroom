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
import stroom.gwt.workbench.client.app.gin.query.QueryScreenGinjector;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocResource;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/PlanBEditor` in the React Storybook, showing Stroom's real
/// `PlanBPresenter` (a Plan B store's editor tab: Settings, Documentation and Permissions) with
/// fake REST replies.
///
/// As `PlanBPlugin` does, the story fetches the document (`GET /planB/v1/{uuid}`), checks the user
/// may edit it and reads it into the editor. React's `docPermission` seam is the Permissions tab's
/// `POST /permission/doc/v1/fetchDocumentUserPermissions`. React's settings `type` names
/// (`StateSettings`) are Stroom's JSON type names (`state`, `metric`, `session`, `trace`).
public final class PlanBEditorStories {

    // The caption of a settings group (SettingsGroup's FormLabel).
    // Differs from React: the groups' captions are labels, not headings
    private static final String GROUP_LABEL = "label.settings-group-label";
    private static final String GROUP = ".settings-group";
    private static final String FORM_GROUP = ".form-group";

    // React's docOf(stateType, settingsType): PlanBDocResource.fetch()
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
            // MetricTimeZoneOffset: the key schema's time zone is an offset
            .get("/planB/v1/planb-METRIC-OFFSET", RestReply.json(doc("METRIC", "metric", MAX_STORE_SIZE,
                    ", \"keySchema\": {\"timeZone\": {\"use\": \"OFFSET\", \"offsetHours\": -1, "
                            + "\"offsetMinutes\": 30}}")
                    .replace("planb-METRIC", "planb-METRIC-OFFSET")))
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
                    play.expect(retainFor.getByRole("textbox", "Amount")).toBeInTheDocument();
                    play.expect(retainFor.getByRole("textbox", "Unit")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // METRIC: the value schema is a Value Size and the store-aggregate tick boxes, and
                // the key schema has a Temporal Resolution and a Time Zone
                .story("Metric", context -> render(context, "planb-METRIC", false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Temporal Resolution", "label"))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Value Size", "label")).toBeInTheDocument();
                    for (final String label : new String[]{
                            "Store Latest Value", "Store Min Value", "Store Max Value", "Store Count", "Store Sum"}) {
                        play.expect(play.getByText(label, "label")).toBeInTheDocument();
                    }
                    // The key schema's TimeZoneWidget
                    play.expect(play.getByText("Time Zone", "label")).toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                })
                // The Time Zone control shows the offset when Use is Offset (TimeZoneWidget)
                .story("MetricTimeZoneOffset", context -> render(context, "planb-METRIC-OFFSET", false))
                .withPlay(play -> {
                    // Differs from React: GWT has one 'Time Zone Offset' group of two spinners, not
                    // 'Offset (hours)' and 'Offset (minutes)', and hides the minutes spinner
                    // (TimeZoneWidget: "Browsers don't support minute offsets so disable this for now")
                    final Query offset = play.findByText("Time Zone Offset", "label");
                    final Play group = play.within(offset.closest(FORM_GROUP));
                    play.expect(group.querySelectorAll("input").nth(0)).toHaveValue("-1");
                    play.expect(group.querySelectorAll("input").nth(1)).toHaveValue("30");
                    play.expect(group.querySelectorAll("input").nth(1)).not().toBeVisible();
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
                    // Differs from React: GWT's trace settings have no 'General' group; their
                    // common groups are Shared File Store, Publishing, Storage and Data Retention
                    play.waitFor(() -> play.expect(play.getByText("Storage", GROUP_LABEL)).toBeInTheDocument());
                    play.expect(play.queryByText("General", GROUP_LABEL)).toBeNull();
                    play.expect(play.getByText("Data Retention", GROUP_LABEL)).toBeInTheDocument();
                    play.expect(play.queryByText("Key Schema", GROUP_LABEL)).toBeNull();
                    play.expect(play.queryByText("Value Schema", GROUP_LABEL)).toBeNull();
                    DocumentEditors.expectNoProblems(play);
                })
                // Editing a setting makes the document dirty, so Save is enabled
                .story("EditEnablesSave", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.waitFor(() -> play.expect(maxStoreSize(play)).toBeInTheDocument());
                    play.click(maxStoreSize(play));
                    play.clear(maxStoreSize(play));
                    play.type(maxStoreSize(play), "512 M");
                    // Differs from React: the GWT text box reports its value when it loses the
                    // focus (a change event), not as it is typed
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocumentEditors.expectNoProblems(play);
                })
                // Read only: the settings are disabled and Save can never be enabled
                .story("ReadOnly", context -> render(context, "planb-STATE", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(maxStoreSize(play)).toHaveAttribute("readonly"));
                    play.expect(play.getByRole("button", "Save is not available as this document is read only"))
                            .toHaveClass("disabled");
                    DocumentEditors.expectNoProblems(play);
                })
                // METRIC read only: the same settings as Metric, when the user may only view the store
                .story("MetricReadOnly", context -> render(context, "planb-METRIC", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Temporal Resolution", "label"))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // SESSION read only: the same settings as Session, when the user may only view the store
                .story("SessionReadOnly", context -> render(context, "planb-SESSION", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Condense Data", GROUP_LABEL))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // TRACE read only: the same settings as Trace, when the user may only view the store
                .story("TraceReadOnly", context -> render(context, "planb-TRACE", true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Storage", GROUP_LABEL)).toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocumentEditors.expectNoProblems(play);
                })
                // Unset schema fields show the Java defaults of their schemas
                .story("UnsetSchemaFieldsShowTheirJavaDefaults", context -> render(context, "planb-STATE", false))
                .withPlay(play -> {
                    // Differs from React: the selection box's value is its text box's value; 'Hash
                    // Length' is in both schemas, so it is found within the Key Schema group
                    play.waitFor(() -> play.expect(selection(play, "Key Schema", "Key Type"))
                            .toHaveValue("Variable"));
                    play.expect(selection(play, "Value Schema", "State Value Type")).toHaveValue("Variable");
                    play.expect(selection(play, "Key Schema", "Hash Length")).toHaveValue("Integer");
                    DocumentEditors.expectNoProblems(play);
                })
                // GeneralSettingsWidget shows DEFAULT_MAX_STORE_SIZE for an unset max store size
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
    // Differs from React: its FormGroup has no identity, so there is no '#planbMaxStoreSize-input'
    private static Query maxStoreSize(final Play play) {
        return play.within(play.getByText("Max Store Size", "label").closest(FORM_GROUP)).querySelector("input");
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
        harness.afterStartUp(() -> DocumentEditors.open(harness, injector.getPlanBPresenter(), docRef,
                resource, res -> res.fetch(docRef.getUuid())));
        return harness.asWidget();
    }
}
