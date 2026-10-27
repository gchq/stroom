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

/// The `App/Editors/ElasticClusterEditor` stories, showing Stroom's real [ElasticClusterPresenter]
/// (an Elastic Cluster's tab: Settings, Documentation and Permissions) with fake REST replies.
///
/// As `ElasticClusterPlugin` does, the story fetches the document
/// (`GET /elasticCluster/v1/{uuid}`) and reads it into the editor ([DocEditors#open]). The other
/// routes:
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /elasticCluster/v1/testCluster` | testing the cluster |
/// | `POST /credentials/findCredentials` (and `getByName` for the saved key) | the API keys |
/// | `POST /elasticCluster/v1/getDefaultHttpClientConfig` | the default HTTP client config |
/// | the Permissions tab's routes | the Permissions tab |
///
/// The cluster's API key is an Access Token credential, picked by name, and its timeouts, TLS and
/// proxy are its HTTP client configuration (Set HTTP Client Config).
public final class ElasticClusterEditorStories {

    private static final String USER_AGENT = "Stroom-Test/1.0";
    private static final DocRef DOC_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-1", "My Cluster");

    // ElasticClusterResource.fetch(): the cluster, with no HTTP client configuration of its own, so
    // the editor fetches the default
    private static final String DOC = """
            {"type": "ElasticCluster", "uuid": "cluster-1", "name": "My Cluster",
              "description": "# Cluster docs",
              "connection": {"connectionUrls": ["https://es-1:9200", "https://es-2:9200"]API_KEY}}""";

    // ElasticClusterResource.getDefaultHttpClientConfig() (as the OpenAI model stories')
    private static final String DEFAULT_HTTP_CLIENT_CONFIG = """
            {"connectionRequestTimeout": {"time": 2, "timeUnit": "MINUTES"},
              "connectionTimeout": {"time": 2, "timeUnit": "MINUTES"}, "cookiesEnabled": false,
              "followRedirects": true, "keepAlive": {"time": 0, "timeUnit": "MINUTES"},
              "maxConnections": 1024, "maxConnectionsPerRoute": 1024, "retries": 0,
              "timeToLive": {"time": 1, "timeUnit": "HOURS"}, "timeout": {"time": 2, "timeUnit": "MINUTES"},
              "tls": {"protocol": "TLSv1.3", "supportedProtocols": ["TLSv1.3", "TLSv1.2"],
                "supportedCiphers": ["TLS_AES_256_GCM_SHA384"], "trustSelfSignedCertificates": false,
                "verifyHostname": true}}""";

    // CredentialsResource.findCredentials(): the API keys (access tokens)
    private static final String API_KEYS = """
            {"values": [{"uuid": "k1", "name": "elastic-key", "credentialType": "ACCESS_TOKEN"},
                {"uuid": "k2", "name": "elastic-key-2", "credentialType": "ACCESS_TOKEN"}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    private static final String TEST_CLUSTER = "/elasticCluster/v1/testCluster";
    private static final String DEFAULT_HTTP_CLIENT_CONFIG_PATH = "/elasticCluster/v1/getDefaultHttpClientConfig";

    // A FormGroup gives its control the group's identity as its id
    // (ElasticClusterSettings.ui.xml)
    private static final String URLS = "#elasticClusterSettingsConnectionURLs";
    private static final String API_KEY = "#elasticClusterSettingsAPIKey";

    private ElasticClusterEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/ElasticClusterEditor", ElasticClusterEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, true, null, false))
                .withPlay(play -> {
                    // ElasticClusterPresenter's tabs: Settings (default), Documentation, Permissions
                    for (final String label : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // The connection form, filled in from the document
                    play.waitFor(() -> play.expect(play.getByText("Connection URLs", "label")).toBeInTheDocument());
                    play.expect(play.getByText("API Key (optional)", "label")).toBeInTheDocument();
                    play.expect(play.querySelector(URLS)).toHaveValue("https://es-1:9200\nhttps://es-2:9200");
                    // The API key's SelectionBox shows the saved key (fetched by name)
                    play.waitFor(() -> play.expect(apiKeyBox(play)).toHaveValue("elastic-key"));
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                .story("EditEnablesSave", context -> render(context, true, null, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(URLS))
                            .toHaveValue("https://es-1:9200\nhttps://es-2:9200"));
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.type(play.querySelector(URLS), "\nhttps://es-3:9200");
                    // The text area reports its change (ValueChangeEvent) when it loses the focus,
                    // not as each key is typed
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // Picking another API key changes the document's apiKeyName
                .story("ChangeApiKey", context -> render(context, true, null, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query box = apiKeyBox(play);
                    play.waitFor(() -> play.expect(box).toHaveValue("elastic-key"));
                    play.click(box);
                    play.click(screen.findByText("elastic-key-2"));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save"))
                            .not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // A cluster with no API key (one that needs no authentication): the box is empty
                // and no key is fetched
                .story("NoApiKey", context -> render(context, false, null, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(URLS))
                            .toHaveValue("https://es-1:9200\nhttps://es-2:9200"));
                    play.expect(apiKeyBox(play)).toHaveValue("");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/getByName").toSpyMatcher());
                    DocEditors.expectNoProblems(play);
                })
                .story("TestConnectionSuccess", context -> render(context, true,
                        "{\"ok\": true, \"message\": \"Connected to 3 nodes.\"}", false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(apiKeyBox(play)).toHaveValue("elastic-key"));
                    play.click(play.findByRole("button", StroomDom.button("Test Connection")));
                    // Stroom's alert is a popup on the page's body
                    play.waitFor(() -> play.expect(screen.getByText("Connection Success")).toBeInTheDocument());
                    play.expect(screen.getByText("Connected to 3 nodes.")).toBeInTheDocument();
                    expectTested(play);
                })
                .story("TestConnectionFailure", context -> render(context, true,
                        "{\"ok\": false, \"message\": \"Connection refused.\"}", false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(apiKeyBox(play)).toHaveValue("elastic-key"));
                    play.click(play.findByRole("button", StroomDom.button("Test Connection")));
                    play.waitFor(() -> play.expect(screen.getByText("Connection Failure")).toBeInTheDocument());
                    play.expect(screen.getByText("Connection refused.")).toBeInTheDocument();
                    expectTested(play);
                })
                // The HTTP client configuration dialog (seeded from the server's default): changing
                // the user agent and OK-ing makes the document dirty, and the change is kept
                .story("SetHttpClientConfig", context -> render(context, true, null, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query save = play.findByRole("button", "Save");
                    showHttpClientConfig(play);
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.type(screen.getByLabelText("User Agent"), USER_AGENT);
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration"))
                            .toBeNull());
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.click(play.getByRole("button", StroomDom.button("Set HTTP Client Config")));
                    play.waitFor(() -> play.expect(screen.getByLabelText("User Agent")).toHaveValue(USER_AGENT));
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration"))
                            .toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, true, null, true))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save (read only)");
                    // Read only, not disabled: every field can be read and copied, none changed
                    play.waitFor(() -> play.expect(play.querySelector(URLS)).toHaveAttribute("readonly"));
                    play.expect(play.querySelector(URLS)).not().toBeDisabled();
                    play.type(play.querySelector(URLS), "x");
                    play.expect(play.querySelector(URLS)).toHaveValue("https://es-1:9200\nhttps://es-2:9200");
                    // A read-only SelectionBox has the selection-box--read-only modifier
                    play.expect(play.querySelector(API_KEY)).toHaveClass("selection-box--read-only");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    // The tab says it is read only, as a status that screen readers announce
                    final Query note = play.getByText("Read only", ".docTab-readOnlyNote");
                    play.expect(note).toBeVisible();
                    play.expect(note.closest(".docTab-readOnlyStatus")).toHaveAttribute("role", "status");
                    DocEditors.expectNoProblems(play);
                })
                // Settings, read only, with nothing typed (ReadOnly types into a field): the partner
                // of Default
                .story("SettingsReadOnly", context -> render(context, true, null, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Connection URLs", "label")).toBeInTheDocument());
                    play.expect(play.querySelector(URLS)).toHaveValue("https://es-1:9200\nhttps://es-2:9200");
                    play.waitFor(() -> play.expect(apiKeyBox(play)).toHaveValue("elastic-key"));
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // The API key's SelectionBox's text box, which shows the chosen key's name
    private static Query apiKeyBox(final Play play) {
        return play.within(play.querySelector(API_KEY)).querySelector(StroomDom.SELECTION_BOX);
    }

    // Opens the HTTP client configuration dialog once the server's default has seeded it
    private static void showHttpClientConfig(final Play play) {
        final Play screen = play.screen();
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.post(DEFAULT_HTTP_CLIENT_CONFIG_PATH).toSpyMatcher()));
        play.click(play.findByRole("button", StroomDom.button("Set HTTP Client Config")));
        play.waitFor(() -> play.expect(screen.getByText("Edit HTTP Client Configuration")).toBeInTheDocument());
        play.expect(screen.getByText("Max Connections", "label")).toBeInTheDocument();
    }

    // The test request carries the form's connection, and the result is the only alert
    private static void expectTested(final Play play) {
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.post(TEST_CLUSTER)
                        .withJsonBodyContaining("{\"connection\": {\"apiKeyName\": \"elastic-key\"}}")
                        .toSpyMatcher());
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledTimes(1);
        DocEditors.expectNoUnhandledRequests(play);
    }

    private static Widget render(final StoryContext context,
                                 final boolean withApiKey,
                                 final String testResult,
                                 final boolean readOnly) {
        final String docJson = DOC.replace("API_KEY", withApiKey
                ? ", \"apiKeyName\": \"elastic-key\""
                : "");
        final RestFixtures.Builder builder = DocEditors.permissionRoutes(RestFixtures.builder())
                .get("/elasticCluster/v1/cluster-1", RestReply.json(docJson))
                .post(DEFAULT_HTTP_CLIENT_CONFIG_PATH, RestReply.json(DEFAULT_HTTP_CLIENT_CONFIG))
                .post("/credentials/findCredentials", RestReply.json(API_KEYS))
                .post("/credentials/getByName", RestReply.json(
                        "{\"uuid\": \"k1\", \"name\": \"elastic-key\", \"credentialType\": \"ACCESS_TOKEN\"}"));
        if (testResult != null) {
            builder.post(TEST_CLUSTER, RestReply.json(testResult));
        }
        final ElasticClusterResource resource = GWT.create(ElasticClusterResource.class);
        return DocEditors.render(context, builder.build(), readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getElasticClusterPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
