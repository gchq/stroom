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


package stroom.gwt.workbench.client.app.main;

import stroom.config.global.client.presenter.GlobalPropertyTabPresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/PropertiesScreen`, showing Stroom's real [GlobalPropertyTabPresenter] (the
/// 'Properties' tab and its property edit dialog) with fake REST replies, for Stroom's
/// `GlobalConfigResource` and `NodeResource`:
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /config/v1/properties` | the properties |
/// | `GET /node/v1/enabled` | the enabled nodes |
/// | `POST /config/v1/nodeProperties/{node}` (nodes the properties request didn't cover) | a node's properties |
/// | `GET /config/v1/properties/{name}` | a property |
/// | `GET /config/v1/clusterProperties/{name}/yamlOverrideValue/{node}` | the dialog's per-node YAML values |
/// | `PUT /config/v1/clusterProperties/{name}` | updating a property |
///
/// The update is checked on the request spy.
public final class PropertiesScreenStories {

    private static final String NODE_PROP = """
            {"id": 1, "name": {"parentParts": ["stroom"], "leafPart": "node"}, "defaultValue": "node1",
              "databaseOverrideValue": {"hasOverride": false}, "yamlOverrideValue": YAML,
              "editable": true, "password": false, "requireRestart": false, "requireUiRestart": false,
              "description": "The name of this node.", "dataTypeName": "String"}""";
    private static final String HOME_PROP = """
            {"id": 2, "name": {"parentParts": ["stroom", "path"], "leafPart": "home"},
              "defaultValue": "/home/stroom", "databaseOverrideValue": {"hasOverride": false},
              "yamlOverrideValue": {"hasOverride": false}, "editable": true, "password": false,
              "requireRestart": false, "requireUiRestart": false, "description": "The home directory.",
              "dataTypeName": "String"}""";
    private static final String NO_OVERRIDE = "{\"hasOverride\": false}";

    private static final String HOME_NAME = "stroom.path.home";

    private PropertiesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/PropertiesScreen", PropertiesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // An unreachable cluster node doesn't fail the load: the rows still render from the
                // nodes that answered, and the node names are shown by a "Show Warnings" button
                .story("ShowWarnings", context -> render(context, fixtures(true)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("stroom.node");
                    final Query button = play.findByTitle("Show Warnings");
                    play.waitFor(() -> play.expect(button).toBeVisible());
                    play.click(button);
                    play.waitFor(() -> play.expect(screen.getByText("Unable to get properties from all nodes:"))
                            .toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "WARN: Unable to get properties from all nodes:");
                    play.expect(screen.getByText(TextMatch.containing("node2"))).toBeInTheDocument();
                    play.expect(screen.getByText(TextMatch.containing("node3"))).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Every node reachable: GWT hides the warnings button
                .story("NoWarningsWhenAllNodesAnswer", context -> render(context, fixtures(false)))
                .withPlay(play -> {
                    play.findByText("stroom.node");
                    // Wait for the other node's reply to be merged in
                    play.findByText("[Multiple values]");
                    play.waitFor(() -> play.expect(play.getByTitle("Show Warnings")).not().toBeVisible());
                    ContentStorySupport.expectNoProblems(play);
                })
                // Cluster-wide value disagreement is flagged red; the edit dialog saves a DB override
                .story("Properties", context -> render(context, fixtures(false)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("stroom.node");
                    play.findByText(HOME_NAME);
                    // stroom.node disagrees across the cluster: "[Multiple values]" in red
                    final Query multiple = play.findByText("[Multiple values]");
                    play.expect(multiple).toHaveStyle("color", "rgb(255, 0, 0)");
                    // Double-click stroom.path.home's row: the edit dialog
                    // GWT's grid handles the events of its cells, so the row's name is pressed rather
                    // than the row (a <tr> with no role)
                    play.click(play.getByText(HOME_NAME));
                    play.dblClick(play.getByText(HOME_NAME));
                    // The dialog has no role="dialog"; it's found by its caption
                    screen.findByText("Application Property - " + HOME_NAME);
                    final Play dialog = screen.within(screen.getByText("Application Property - " + HOME_NAME)
                            .closest(StroomDom.DIALOG));
                    // The per-node values are in a separate popup, opened by the icon by Effective
                    // Value; both nodes agree on this property, so it's the INFO icon
                    final Query info = dialog.findByTitle("All nodes have the same effective value");
                    play.waitFor(() -> play.expect(info).toBeVisible());
                    play.click(info);
                    final Query clusterCaption = screen.findByText("Cluster values - " + HOME_NAME);
                    final Play cluster = screen.within(clusterCaption.closest(StroomDom.DIALOG));
                    for (final String heading : new String[]{"Effective Value", "Count", "Source", "Node"}) {
                        play.expect(cluster.getByRole("columnheader", TextMatch.startingWith(heading)))
                                .toBeInTheDocument();
                    }
                    // Both nodes agree, so they group under one value with a count of 2
                    play.waitFor(() -> play.expect(cluster.getByText("2")).toBeInTheDocument());
                    play.click(cluster.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText("Cluster values - " + HOME_NAME)).toBeNull());
                    // Enable the database override, set a value, OK: the property is updated
                    play.click(dialog.getByLabelText("Set Database value"));
                    // The 'Database Value' label is for the panel holding the text area (and a password
                    // box), not the text area itself
                    final Query dbValue = dialog.within(dialog.getByText("Database Value", "label")
                            .closest(".form-group")).querySelector("textarea");
                    play.clear(dbValue);
                    play.type(dbValue, "/data/stroom");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/config/v1/clusterProperties/" + HOME_NAME)
                                    .withJsonBodyContaining("{\"databaseOverrideValue\": "
                                            + "{\"hasOverride\": true, \"value\": \"/data/stroom\"}}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static String list(final String nodeName, final String nodeYaml) {
        return "{\"values\": [" + NODE_PROP.replace("YAML", nodeYaml) + ", " + HOME_PROP + "], "
                + "\"pageResponse\": {\"offset\": 0, \"length\": 2, \"total\": 2, \"exact\": true}, "
                + "\"nodeName\": \"" + nodeName + "\"}";
    }

    private static RestFixtures fixtures(final boolean unreachableNodes) {
        final RestFixtures.Builder builder = RestFixtures.builder()
                .post("/config/v1/properties", RestReply.json(list("node1", NO_OVERRIDE)));
        if (unreachableNodes) {
            builder.get("/node/v1/enabled", RestReply.json("[\"node1\", \"node2\", \"node3\"]"))
                    .post("/config/v1/nodeProperties/node2", RestReply.error(500, "node2 is unreachable"))
                    .post("/config/v1/nodeProperties/node3", RestReply.error(500, "node3 is unreachable"));
        } else {
            // node2 sees a YAML override on stroom.node, so its effective value differs
            builder.get("/node/v1/enabled", RestReply.json("[\"node1\", \"node2\"]"))
                    .post("/config/v1/nodeProperties/node2", RestReply.json(list("node2",
                            "{\"hasOverride\": true, \"value\": \"node2-override\"}")));
        }
        return builder
                .get("/config/v1/properties/" + HOME_NAME, RestReply.json(HOME_PROP))
                .get("/config/v1/clusterProperties/" + HOME_NAME + "/yamlOverrideValue/*",
                        RestReply.json(NO_OVERRIDE))
                .put("/config/v1/clusterProperties/" + HOME_NAME, PropertiesScreenStories::echo)
                .build();
    }

    private static RestReply echo(final RecordedRequest request) {
        return RestReply.json(request.getBody());
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                // The warnings are shown in Stroom's real alert dialog
                .realAlerts()
                .build();
        harness.afterStartUp(() -> harness.addContent(injector.getGlobalPropertyTabPresenter()));
        return harness.asWidget();
    }
}
