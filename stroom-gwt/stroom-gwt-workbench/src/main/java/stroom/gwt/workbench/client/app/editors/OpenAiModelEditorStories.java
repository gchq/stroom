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
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.openai.client.presenter.OpenAIModelPresenter;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.openai.shared.OpenAIModelResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Editors/OpenAiModelEditor` in the React Storybook, showing Stroom's real
/// [OpenAIModelPresenter] (an OpenAI Model's tab: Settings, Documentation and Permissions) with
/// fake REST replies.
///
/// As `OpenAIModelPlugin` does, the story fetches the document (`GET /openAIModel/v1/{uuid}`) and
/// reads it into the editor ([DocEditors#open]). React's seams become routes:
///
/// | React seam | Stroom endpoint |
/// |---|---|
/// | `validate` | `POST /openAIModel/v1/validate` |
/// | `listApiKeys` | `POST /credentials/findCredentials` (and `getByName` for the saved key) |
/// | `getDefaultHttpClientConfig` | `POST /openAIModel/v1/getDefaultHttpClientConfig` |
/// | `listKeyStores` | not used: the TLS dialog's key stores are text boxes in GWT |
/// | `docPermission` | the Permissions tab's routes |
public final class OpenAiModelEditorStories {

    private static final String USER_AGENT = "Stroom-Test/1.0";
    private static final DocRef DOC_REF = new DocRef(OpenAIModelDoc.TYPE, "model-1", "GPT-4o");

    // OpenAIModelResource.fetch(): React's MODEL_DOC
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

    // CredentialsResource.findCredentials(): React's API_KEYS (access tokens)
    private static final String API_KEYS = """
            {"values": [{"uuid": "k1", "name": "openai-key", "credentialType": "ACCESS_TOKEN"},
                {"uuid": "k2", "name": "azure-key", "credentialType": "ACCESS_TOKEN"}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    private static final String VALIDATE = "/openAIModel/v1/validate";

    // Differs from React: a FormGroup gives its control the group's identity as its id
    // (OpenAIModelSettingsViewImpl.ui.xml), not React's '<name>-input'
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
                    // Differs from React: the box is a text box showing the key's name
                    play.waitFor(() -> play.expect(play.within(play.querySelector(API_KEY))
                            .querySelector(StroomDom.SELECTION_BOX)).toHaveValue("openai-key"));
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // The numeric fields are ValueSpinners, and every FormGroup has help text
                .story("SpinnersAndHelpText", context -> render(context, null, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: a ValueSpinner's field is a plain text box, with no
                    // spinbutton role or aria-valuemin/max (GWT bug), so the play checks its value
                    final Query context = play.within(play.querySelector(MAX_CONTEXT)).getByRole("textbox");
                    final Query dimensions = play.within(play.querySelector(EMBEDDING_DIMENSIONS))
                            .getByRole("textbox");
                    play.waitFor(() -> play.expect(context).toHaveValue("128000"));
                    play.expect(dimensions).toHaveValue("1536");
                    // The up arrow increments by one and makes the document dirty.
                    // Differs from React: the arrow is a div with no button role or name, and steps
                    // on its mousedown
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.click(play.within(play.querySelector(MAX_CONTEXT)).querySelector(".valueSpinner-arrowUp"));
                    play.waitFor(() -> play.expect(context).toHaveValue("128001"));
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    // Every settings FormGroup has GWT's help text, so a help button
                    play.expect(play.getAllByRole("button", TextMatch.endingWith("- Click for help")))
                            .toHaveLength(6);
                    play.click(play.getByRole("button", "Base URL (optional) - Click for help"));
                    // Differs from React: the help is a popup on the page's body (the help is also in
                    // the page, hidden, as the field's description)
                    play.expect(screen.findByText(TextMatch.containing("The base URL of an OpenAI-compatible API"),
                                    ".help-button-tooltip *"))
                            .toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                .story("EditEnablesSave", context -> render(context, null, false))
                .withPlay(play -> {
                    final Query save = play.findByRole("button", "Save");
                    play.waitFor(() -> play.expect(play.querySelector(MODEL_ID)).toHaveValue("gpt-4o"));
                    play.expect(save).toHaveClass("disabled");
                    play.type(play.querySelector(MODEL_ID), "-mini");
                    // Differs from React: the text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
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
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save")).not().toHaveClass("disabled"));
                    DocEditors.expectNoProblems(play);
                })
                .story("TestModelSuccess", context -> render(context,
                        "{\"ok\": true, \"message\": \"Model responded in 210ms.\"}", false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Test Model")));
                    // Differs from React: Stroom's alert is a popup on the page's body
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
                    play.expect(save).toHaveClass("disabled");

                    play.click(play.getByRole("button", StroomDom.button("Set Http Client Config")));
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
                    // Differs from React: Stroom's dialogs have no role="dialog"
                    final Play tlsDialog = play.within(protocol.closest(StroomDom.DIALOG));
                    play.click(tlsDialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP TLS Configuration")).toBeNull());
                    play.type(screen.getByLabelText("User Agent"), USER_AGENT);
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration"))
                            .toBeNull());
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    // The user agent is kept (it used to be lost on OK)
                    play.click(play.getByRole("button", StroomDom.button("Set Http Client Config")));
                    play.waitFor(() -> play.expect(screen.getByLabelText("User Agent")).toHaveValue(USER_AGENT));
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration"))
                            .toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, null, true))
                .withPlay(play -> {
                    final Query save = play.findByRole("button",
                            "Save is not available as this document is read only");
                    play.waitFor(() -> play.expect(play.querySelector(BASE_URL)).toBeDisabled());
                    // Differs from React: a disabled SelectionBox disables its text box (there is
                    // no 'selection-box--disabled' class)
                    play.expect(play.within(play.querySelector(API_KEY)).querySelector(StroomDom.SELECTION_BOX))
                            .toBeDisabled();
                    play.expect(save).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                });
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
                .post("/credentials/findCredentials", RestReply.json(API_KEYS))
                .post("/credentials/getByName", RestReply.json(
                        "{\"uuid\": \"k1\", \"name\": \"openai-key\", \"credentialType\": \"ACCESS_TOKEN\"}"));
        if (testResult != null) {
            builder.post(VALIDATE, RestReply.json(testResult));
        }
        final OpenAIModelResource resource = GWT.create(OpenAIModelResource.class);
        return DocEditors.render(context, builder.build(), readOnly, (harness, injector) -> DocEditors.open(harness,
                DOC_REF,
                injector.getOpenAIModelPresenter(),
                DocResource.of(
                        restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                        (restFactory, doc) -> restFactory.create(resource)
                                .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
