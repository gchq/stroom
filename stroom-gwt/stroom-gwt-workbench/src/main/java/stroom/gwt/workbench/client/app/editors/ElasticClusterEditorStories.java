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
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
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
import stroom.search.elastic.client.presenter.ElasticClusterPresenter;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/ElasticClusterEditor` in the React Storybook, showing Stroom's
/// real [ElasticClusterPresenter] (an Elastic Cluster's tab: Settings, Documentation and
/// Permissions) with fake REST replies.
///
/// As `ElasticClusterPlugin` does, the story fetches the document
/// (`GET /elasticCluster/v1/{uuid}`) and reads it into the editor ([DocEditors#open]). React's
/// `testCluster` seam → `POST /elasticCluster/v1/testCluster`, `docPermission` → the Permissions
/// tab's routes.
public final class ElasticClusterEditorStories {

    private static final DocRef DOC_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-1", "My Cluster");

    // ElasticClusterResource.fetch(): React's CLUSTER_DOC
    private static final String DOC = """
            {"type": "ElasticCluster", "uuid": "cluster-1", "name": "My Cluster",
              "description": "# Cluster docs",
              "connection": {"connectionUrls": ["https://es-1:9200", "https://es-2:9200"],
                "useAuthentication": AUTH, "apiKeyId": "key-abc", "apiKeySecret": "secret-xyz",
                "connectionTimeoutMillis": 5000, "responseTimeoutMillis": 30000}}""";

    private static final String TEST_CLUSTER = "/elasticCluster/v1/testCluster";

    // Differs from React: a FormGroup gives its control the group's identity as its id
    // (ElasticClusterSettingsViewImpl.ui.xml), not React's '<name>-input'
    private static final String URLS = "#elasticClusterSettingsConnectionURLs";
    private static final String API_KEY_ID = "#elasticClusterSettingsAPIKeyId";
    private static final String API_KEY_SECRET = "#elasticClusterSettingsAPIKeySecret";

    private ElasticClusterEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/ElasticClusterEditor", ElasticClusterEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, fixtures(true, null), false))
                .withPlay(play -> {
                    // ElasticClusterPresenter's tabs: Settings (default), Documentation, Permissions
                    for (final String label : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // The connection form, filled in from the document
                    play.waitFor(() -> play.expect(play.getByText("Connection URLs", "label")).toBeInTheDocument());
                    play.expect(play.getByText("CA certificate (PEM format)", "label")).toBeInTheDocument();
                    play.expect(play.getByText("API key ID", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Use authentication")).toBeInTheDocument();
                    play.expect(play.querySelector(URLS)).toHaveValue("https://es-1:9200\nhttps://es-2:9200");
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                .story("EditEnablesSave", context -> render(context, fixtures(true, null), false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(API_KEY_ID)).toHaveValue("key-abc"));
                    play.expect(save).toHaveClass("disabled");
                    play.type(play.querySelector(API_KEY_ID), "-2");
                    // Differs from React: the text box reports its change (ValueChangeEvent) when it
                    // loses the focus, not as each key is typed
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocEditors.expectNoProblems(play);
                })
                .story("TestConnectionSuccess", context -> render(context,
                        fixtures(true, "{\"ok\": true, \"message\": \"Connected to 3 nodes.\"}"), false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Test Connection")));
                    // Differs from React: Stroom's alert is a popup on the page's body
                    play.waitFor(() -> play.expect(screen.getByText("Connection Success")).toBeInTheDocument());
                    play.expect(screen.getByText("Connected to 3 nodes.")).toBeInTheDocument();
                    expectTested(play);
                })
                .story("TestConnectionFailure", context -> render(context,
                        fixtures(true, "{\"ok\": false, \"message\": \"Connection refused.\"}"), false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Test Connection")));
                    play.waitFor(() -> play.expect(screen.getByText("Connection Failure")).toBeInTheDocument());
                    play.expect(screen.getByText("Connection refused.")).toBeInTheDocument();
                    expectTested(play);
                })
                // The API key fields are disabled unless 'Use authentication' is ticked
                // (updateAuthenticationControlEnabledState)
                .story("AuthGating", context -> render(context, fixtures(false, null), false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(API_KEY_ID)).toHaveValue("key-abc"));
                    play.expect(play.querySelector(API_KEY_ID)).toBeDisabled();
                    play.expect(play.querySelector(API_KEY_SECRET)).toBeDisabled();
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, fixtures(true, null), true))
                .withPlay(play -> {
                    final Query save = play.findByRole("button",
                            "Save is not available as this document is read only");
                    play.waitFor(() -> play.expect(play.querySelector(URLS)).toBeDisabled());
                    play.expect(save).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                });
    }

    // The test request carries the form's connection, and the result is the only alert
    private static void expectTested(final Play play) {
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.post(TEST_CLUSTER)
                        .withJsonBodyContaining("{\"connection\": {\"apiKeyId\": \"key-abc\"}}")
                        .toSpyMatcher());
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledTimes(1);
        DocEditors.expectNoUnhandledRequests(play);
    }

    private static RestFixtures fixtures(final boolean useAuthentication, final String testResult) {
        final RestFixtures.Builder builder = DocEditors.permissionRoutes(RestFixtures.builder())
                .get("/elasticCluster/v1/cluster-1", RestReply.json(DOC.replace("AUTH",
                        String.valueOf(useAuthentication))));
        if (testResult != null) {
            builder.post(TEST_CLUSTER, RestReply.json(testResult));
        }
        return builder.build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures, final boolean readOnly) {
        final ElasticClusterResource resource = GWT.create(ElasticClusterResource.class);
        return DocEditors.render(context, fixtures, readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getElasticClusterPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
