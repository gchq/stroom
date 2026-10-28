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
import stroom.openai.client.component.OpenAIModelEditor;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.openai.shared.OpenAIModelResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;

/// The `App/Editors/OpenAiModelEditor` stories, showing Stroom's real [OpenAIModelEditor] (an
/// OpenAI Model's tab: Settings, Documentation and Permissions) with fake REST replies.
///
/// As `OpenAIModelPlugin` does, the story fetches the document (`GET /openAIModel/v1/{uuid}`) and
/// reads it into the editor ([DocEditors#open]). The other routes:
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /openAIModel/v1/validate` | validating the model |
/// | `POST /credentials/findCredentials` (and `getByName` for the saved key) | the API keys |
/// | `POST /credentials/findCredentials` for key stores | the TLS dialog's key and trust stores |
/// | `POST /openAIModel/v1/getDefaultHttpClientConfig` | the default HTTP client config |
/// | the Permissions tab's routes | the Permissions tab |
///
/// The TLS dialog's key and trust stores are credentials pickers, which ask for both kinds of key
/// store credential (an uploaded Key Store and a Key Store File); the reply has one of each.
public final class OpenAiModelEditorStories {

    private static final String USER_AGENT = "Stroom-Test/1.0";
    private static final DocRef DOC_REF = new DocRef(OpenAIModelDoc.TYPE, "model-1", "GPT-4o");

    // OpenAIModelResource.fetch(): the model
    private static final String DOC = """
            {"type": "OpenAIModel", "uuid": "model-1", "name": "GPT-4o",
              "baseUrl": "https://api.openai.com/v1", "apiKeyName": "openai-key", "modelId": "gpt-4o",
              "maxContextWindowTokens": 128000, "reasoningEffort": "medium",
              "embeddingModelDimensions": 1536, "description": "# Model docs"}""";

    // OpenAIModelResource.getDefaultHttpClientConfig() (the gwt-suite corpus's, trimmed)
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
            {"values": [{"uuid": "k1", "name": "openai-key", "credentialType": "ACCESS_TOKEN"},
                {"uuid": "k2", "name": "azure-key", "credentialType": "ACCESS_TOKEN"}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    // CredentialsResource.findCredentials() for key stores: an uploaded key store and a key store
    // file
    private static final String KEY_STORES = """
            {"values": [{"uuid": "ks1", "name": "uploaded-store", "credentialType": "KEY_STORE",
                  "keyStoreType": "PKCS12"},
                {"uuid": "ks2", "name": "managed-store", "credentialType": "KEY_STORE_FILE",
                  "keyStoreType": "PKCS12", "keyStorePath": "/etc/stroom/managed.p12"}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    private static final String TLS_KEY_STORE = "#httpTlsConfigKeyStore";

    private static final String VALIDATE = "/openAIModel/v1/validate";

    // A FormGroup gives its control the group's identity as its id
    // (OpenAIModelSettings.ui.xml)
    private static final String BASE_URL = "#openAIModelBaseUrl";
    private static final String MODEL_ID = "#openAIModelId";
    private static final String API_KEY = "#openAIModelApiKey";
    private static final String MAX_CONTEXT = "#openAIMaxContextWindowTokens";
    private static final String EMBEDDING_DIMENSIONS = "#embeddingModelDimensions";
    private static final String TLS_PROTOCOL = "#httpTlsConfigProtocol";

    private OpenAiModelEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/OpenAiModelEditor", OpenAiModelEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, null, false))
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.waitFor(() -> play.expect(play.getByText("Base URL (optional)", "label"))
                            .toBeInTheDocument());
                    play.expect(play.getByText("API Key (optional)", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Model ID", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Maximum context window tokens", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Embedding model dimensions", "label")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.querySelector(MODEL_ID)).toHaveValue("gpt-4o"));
                    // The API key's SelectionBox shows the saved key (fetched by name).
                    // The box is a text box showing the key's name
                    play.waitFor(() -> play.expect(play.within(play.querySelector(API_KEY))
                            .querySelector(StroomDom.SELECTION_BOX)).toHaveValue("openai-key"));
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // The numeric fields are ValueSpinners, and every FormGroup has help text
                .story("SpinnersAndHelpText", context -> render(context, null, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // A ValueSpinner's field is a spin button, with its value and bounds
                    final Query context = play.within(play.querySelector(MAX_CONTEXT)).getByRole("spinbutton");
                    final Query dimensions = play.within(play.querySelector(EMBEDDING_DIMENSIONS))
                            .getByRole("spinbutton");
                    play.waitFor(() -> play.expect(context).toHaveValue("128000"));
                    play.expect(dimensions).toHaveValue("1536");
                    // The increase arrow steps by one and makes the document dirty
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.click(play.within(play.querySelector(MAX_CONTEXT)).getByTitle("Increase value"));
                    play.waitFor(() -> play.expect(context).toHaveValue("128001"));
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    // Every settings FormGroup has GWT's help text, so a help button
                    play.expect(play.getAllByRole("button", TextMatch.endingWith("- Click for help (F1)")))
                            .toHaveLength(6);
                    play.click(play.getByRole("button", "Base URL (optional) - Click for help (F1)"));
                    // The help is a popup on the page's body (the help is also in the page, hidden,
                    // as the field's description)
                    play.expect(screen.findByText(TextMatch.containing("The base URL of an OpenAI-compatible API"),
                                    ".help-popup *"))
                            .toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                .story("EditEnablesSave", context -> render(context, null, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(MODEL_ID)).toHaveValue("gpt-4o"));
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    play.type(play.querySelector(MODEL_ID), "-mini");
                    // The text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                // Picking another API key changes the document's apiKeyName
                .story("ChangeApiKey", context -> render(context, null, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query box = play.within(play.querySelector(API_KEY)).querySelector(StroomDom.SELECTION_BOX);
                    play.waitFor(() -> play.expect(box).toHaveValue("openai-key"));
                    play.click(box);
                    play.click(screen.findByText("azure-key"));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save"))
                            .not().toHaveAttribute("aria-disabled"));
                    DocEditors.expectNoProblems(play);
                })
                .story("TestModelSuccess", context -> render(context,
                        "{\"ok\": true, \"message\": \"Model responded in 210ms.\"}", false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Test Model")));
                    // Stroom's alert is a popup on the page's body
                    play.waitFor(() -> play.expect(screen.getByText("Model Validation Successful"))
                            .toBeInTheDocument());
                    play.expect(screen.getByText("Model responded in 210ms.")).toBeInTheDocument();
                    expectValidated(play);
                })
                .story("TestModelFailure", context -> render(context,
                        "{\"ok\": false, \"message\": \"Invalid API key.\"}", false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Test Model")));
                    play.waitFor(() -> play.expect(screen.getByText("Model Validation Failed")).toBeInTheDocument());
                    play.expect(screen.getByText("Invalid API key.")).toBeInTheDocument();
                    expectValidated(play);
                })
                // The HTTP client configuration dialog (seeded from the server's default) and its
                // nested TLS dialog: changing the protocol and OK-ing both makes the document dirty
                .story("SetHttpClientConfig", context -> render(context, null, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/openAIModel/v1/getDefaultHttpClientConfig").toSpyMatcher()));
                    play.expect(save).toHaveAttribute("aria-disabled", "true");

                    play.click(play.getByRole("button", StroomDom.button("Set HTTP Client Config")));
                    play.waitFor(() -> play.expect(screen.getByText("Edit HTTP Client Configuration"))
                            .toBeInTheDocument());
                    play.expect(screen.getByText("Max Connections", "label")).toBeInTheDocument();

                    play.click(screen.getByRole("button", StroomDom.button("Set HTTP TLS Config")));
                    play.waitFor(() -> play.expect(screen.getByText("Edit HTTP TLS Configuration"))
                            .toBeInTheDocument());
                    final Query protocol = screen.querySelector(TLS_PROTOCOL);
                    play.expect(protocol).toHaveValue("TLSv1.3");
                    play.clear(protocol);
                    play.type(protocol, "TLSv1.2");
                    // OK the TLS dialog (both dialogs have an OK), then the HTTP dialog.
                    // Stroom's dialogs have no role="dialog"
                    final Play tlsDialog = play.within(protocol.closest(StroomDom.DIALOG));
                    play.click(tlsDialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP TLS Configuration")).toBeNull());
                    play.type(screen.getByLabelText("User Agent"), USER_AGENT);
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration"))
                            .toBeNull());
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    // The user agent is kept (it used to be lost on OK)
                    play.click(play.getByRole("button", StroomDom.button("Set HTTP Client Config")));
                    play.waitFor(() -> play.expect(screen.getByLabelText("User Agent")).toHaveValue(USER_AGENT));
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration"))
                            .toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, null, true))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save (read only)");
                    // Read only, not disabled: the fields can be read and copied but not changed
                    play.waitFor(() -> play.expect(play.querySelector(BASE_URL)).toHaveAttribute("readonly"));
                    play.expect(play.querySelector(BASE_URL)).not().toBeDisabled();
                    // A read-only SelectionBox has the selection-box--read-only modifier
                    play.expect(play.querySelector(API_KEY)).toHaveClass("selection-box--read-only");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // Settings, read only: the partner of Default
                .story("SettingsReadOnly", context -> render(context, null, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Base URL (optional)", "label"))
                            .toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.querySelector(MODEL_ID)).toHaveValue("gpt-4o"));
                    play.waitFor(() -> play.expect(play.within(play.querySelector(API_KEY))
                            .querySelector(StroomDom.SELECTION_BOX)).toHaveValue("openai-key"));
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                })
                // The HTTP client configuration dialog, editable: the partner of
                // HttpClientConfigReadOnly
                .story("HttpClientConfigEditable", context -> render(context, null, false))
                .withPlay(play -> {
                    showHttpClientConfig(play);
                    // Each tick box has help beside its label, as the dialog's other fields do
                    play.expect(play.screen().getByTitle("Cookies Enabled - Click for help (F1)")).toBeVisible();
                    play.expect(play.screen().getByTitle("Follow Redirects - Click for help (F1)")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                })
                // The HTTP client configuration dialog, read only: the partner of
                // HttpClientConfigEditable
                .story("HttpClientConfigReadOnly", context -> render(context, null, true))
                .withPlay(play -> {
                    showHttpClientConfig(play);
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                })
                // The TLS dialog's Key Store picker offers both kinds of key store credential: an
                // uploaded Key Store and a Key Store File on the nodes
                .story("TlsKeyStorePicker", context -> render(context, null, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    showHttpClientConfig(play);
                    play.click(screen.getByRole("button", StroomDom.button("Set HTTP TLS Config")));
                    play.waitFor(() -> play.expect(screen.getByText("Edit HTTP TLS Configuration"))
                            .toBeInTheDocument());
                    final Query keyStore = screen.within(screen.querySelector(TLS_KEY_STORE))
                            .querySelector(StroomDom.SELECTION_BOX);
                    play.click(keyStore);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/findCredentials")
                                    .withBody("asks for key stores", OpenAiModelEditorStories::asksForKeyStores)
                                    .toSpyMatcher()));
                    play.expect(screen.findByText("managed-store")).toBeInTheDocument();
                    play.expect(screen.getByText("uploaded-store")).toBeInTheDocument();
                    play.click(screen.getByText("managed-store"));
                    play.waitFor(() -> play.expect(keyStore).toHaveValue("managed-store"));
                    DocEditors.expectNoProblems(play);
                });
    }

    // Whether a find asks for both kinds of key store credential, and nothing else
    private static boolean asksForKeyStores(final String body) {
        final Object json = body == null
                ? null
                : JsonValues.parse(body);
        final Object types = json instanceof final Map<?, ?> map
                ? map.get("credentialTypes")
                : null;
        return types instanceof final List<?> list
               && list.size() == 2
               && list.contains("KEY_STORE")
               && list.contains("KEY_STORE_FILE");
    }

    // Opens the HTTP client configuration dialog once the server's default has seeded it
    private static void showHttpClientConfig(final Play play) {
        final Play screen = play.screen();
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.post("/openAIModel/v1/getDefaultHttpClientConfig").toSpyMatcher()));
        play.click(play.findByRole("button", StroomDom.button("Set HTTP Client Config")));
        play.waitFor(() -> play.expect(screen.getByText("Edit HTTP Client Configuration")).toBeInTheDocument());
        play.expect(screen.getByText("Max Connections", "label")).toBeInTheDocument();
    }

    // The validation request carries the form's model, and the result is the only alert
    private static void expectValidated(final Play play) {
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.post(VALIDATE)
                        .withJsonBodyContaining("{\"modelId\": \"gpt-4o\", \"apiKeyName\": \"openai-key\"}")
                        .toSpyMatcher());
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledTimes(1);
        DocEditors.expectNoUnhandledRequests(play);
    }

    private static Widget render(final StoryContext context, final String testResult, final boolean readOnly) {
        final RestFixtures.Builder builder = DocEditors.permissionRoutes(RestFixtures.builder())
                .get("/openAIModel/v1/model-1", RestReply.json(DOC))
                .put("/openAIModel/v1/model-1", request -> RestReply.json(request.getBody()))
                .post("/openAIModel/v1/getDefaultHttpClientConfig", RestReply.json(DEFAULT_HTTP_CLIENT_CONFIG))
                .route(RequestMatcher.post("/credentials/findCredentials")
                                .withBody("asks for key stores", OpenAiModelEditorStories::asksForKeyStores),
                        RestReply.json(KEY_STORES))
                .post("/credentials/findCredentials", RestReply.json(API_KEYS))
                .post("/credentials/getByName", RestReply.json(
                        "{\"uuid\": \"k1\", \"name\": \"openai-key\", \"credentialType\": \"ACCESS_TOKEN\"}"));
        if (testResult != null) {
            builder.post(VALIDATE, RestReply.json(testResult));
        }
        final OpenAIModelResource resource = GWT.create(OpenAIModelResource.class);
        return DocEditors.render(context, builder.build(), readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getOpenAIModelEditor(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
