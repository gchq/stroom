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
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.gitrepo.client.presenter.GitRepoPresenter;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.gitrepo.shared.GitRepoResource;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Supplier;

/// Stories matching `App/Editors/GitRepoEditor` in the React Storybook, showing Stroom's real
/// [GitRepoPresenter] (a Git Repository's tab: Settings, Documentation and Permissions) with fake
/// REST replies.
///
/// As `GitRepoPlugin` does, the story fetches the document (`GET /gitRepo/v1/{uuid}`) and reads it
/// into the editor ([DocEditors#open]); Save sends it to `PUT /gitRepo/v1/{uuid}`. React's seams
/// become routes:
///
/// | React seam | Stroom endpoint |
/// |---|---|
/// | `api.push` | `POST /gitRepo/v1/pushToGit` |
/// | `api.pull` | `POST /gitRepo/v1/pullFromGit` |
/// | `api.checkUpdates` | `POST /gitRepo/v1/areUpdatesAvailable` |
/// | `api.getDefaultHttpClientConfig` | `POST /gitRepo/v1/getDefaultHttpClientConfig` |
/// | `loadCredentials` | `POST /credentials/findCredentialsWithPermissions` |
/// | `resolveCredential` | `POST /credentials/getByName` |
/// | `refreshExplorer` | a spy on `RefreshExplorerTreeEvent` |
///
/// React's `CRED_LOADS` and `DEFAULT_CONFIG_FETCHES` counters are the request spy's calls.
public final class GitRepoEditorStories {

    /// The name of the spy recording the explorer tree refreshes (React's `refreshExplorer`).
    static final String REFRESH_EXPLORER = "refreshExplorer";

    private static final DocRef DOC_REF = new DocRef(GitRepoDoc.TYPE, "git-1", "My Repo");

    // GitRepoResource.fetch(): React's GIT_DOC (no HTTP client configuration, so the editor seeds
    // it from the server's default)
    private static final String DOC = """
            {"type": "GitRepo", "uuid": "git-1", "name": "My Repo", URL
              "branch": "main", "path": "content", "credentialName": "my-token", COMMIT
              "autoPush": false, "description": "# Git docs"}""";

    private static final String GIT_URL = "\"url\": \"https://github.com/example/repo.git\",";

    // GitRepoResource.getDefaultHttpClientConfig() (the gwt-suite corpus's, trimmed), with React's
    // recognisable maxConnections of 77
    private static final String DEFAULT_HTTP_CLIENT_CONFIG = """
            {"connectionRequestTimeout": {"time": 2, "timeUnit": "MINUTES"},
              "connectionTimeout": {"time": 2, "timeUnit": "MINUTES"}, "cookiesEnabled": false,
              "followRedirects": true, "keepAlive": {"time": 0, "timeUnit": "MINUTES"},
              "maxConnections": 77, "maxConnectionsPerRoute": 1024, "retries": 0,
              "timeToLive": {"time": 1, "timeUnit": "HOURS"}, "timeout": {"time": 2, "timeUnit": "MINUTES"},
              "tls": {"protocol": "TLSv1.3", "supportedProtocols": ["TLSv1.3", "TLSv1.2"],
                "supportedCiphers": ["TLS_AES_256_GCM_SHA384"], "trustSelfSignedCertificates": false,
                "verifyHostname": true}}""";

    // CredentialsResource.findCredentials(): React's credentialsFixture
    private static final String CREDENTIALS = """
            {"values": [{"uuid": "c1", "name": "my-ssh-key", "credentialType": "SSH_KEY"},
                {"uuid": "c2", "name": "my-token", "credentialType": "ACCESS_TOKEN"}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    private static final String NO_CREDENTIALS = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}""";

    private static final String MY_TOKEN = "{\"uuid\": \"c2\", \"name\": \"my-token\", \"credentialType\": "
            + "\"ACCESS_TOKEN\"}";

    private static final String FIND_CREDENTIALS = "/credentials/findCredentials";
    private static final String GET_DEFAULT_CONFIG = "/gitRepo/v1/getDefaultHttpClientConfig";
    private static final String UPDATE = "/gitRepo/v1/git-1";

    // Differs from React: a FormGroup gives its control the group's identity as its id
    // (GitRepoSettingsViewImpl.ui.xml), not React's '<name>-input'
    private static final String URL_INPUT = "#txtGitUrl";
    private static final String COMMIT_MESSAGE = "#commitMessage";

    private GitRepoEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/GitRepoEditor", GitRepoEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", context -> render(context, new Fixtures()))
                .withPlay(play -> {
                    for (final String label : new String[]{"Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    // Settings shows the git fields, and (no commit) the Auto-push group and Push
                    play.waitFor(() -> play.expect(play.getByText("Git repository URL", "label")).toBeInTheDocument());
                    play.expect(play.getByText("Git branch", "label")).toBeInTheDocument();
                    play.expect(play.getByText("Automatically push", "label")).toBeVisible();
                    play.expect(play.getByRole("button", StroomDom.button("Push to Git"))).toBeInTheDocument();
                    play.expect(play.getByRole("button", StroomDom.button("Pull from Git"))).toBeInTheDocument();
                    play.expect(play.getByRole("button", StroomDom.button("Check for updates"))).toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.querySelector(URL_INPUT))
                            .toHaveValue("https://github.com/example/repo.git"));
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // A commit hash makes the repository pull-only: Auto-push and Push are hidden
                .story("CommitHidesPush", context -> render(context, new Fixtures().commit("abc123")))
                .withPlay(play -> {
                    play.findByRole("button", StroomDom.button("Pull from Git"));
                    play.expect(play.queryByRole("button", StroomDom.button("Push to Git"))).toBeNull();
                    // Differs from React: GWT hides the group (display: none) rather than removing it
                    play.expect(play.getByText("Automatically push", "label")).not().toBeVisible();
                    play.expect(play.getByRole("button", StroomDom.button("Pull from Git"))).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // Push opens the commit message dialog; OK with no message warns, and the dialog stays
                .story("PushViaCommitDialog", context -> render(context, new Fixtures()
                        .reply("/gitRepo/v1/pushToGit", "{\"ok\": true, \"message\": \"Pushed 5 files.\"}")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Push to Git")));
                    // Differs from React: the dialog is a popup on the page's body
                    final Query ok = screen.findByRole("button", StroomDom.button("OK"));
                    play.expect(ok).not().toBeDisabled();
                    play.click(ok);
                    play.waitFor(() -> play.expect(screen.getByText("Please enter a commit message"))
                            .toBeInTheDocument());
                    // Dismiss the warning; the commit dialog is still there to correct
                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    play.type(screen.querySelector(COMMIT_MESSAGE), "Initial commit");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("Push Success")).toBeInTheDocument());
                    play.expect(screen.getByText("Pushed 5 files.")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/gitRepo/v1/pushToGit")
                                    .withJsonBodyContaining("{\"commitMessage\": \"Initial commit\"}")
                                    .toSpyMatcher());
                    DocEditors.expectNoUnhandledRequests(play);
                })
                // Pull shows the result, and closing the alert refreshes the explorer tree
                .story("PullSuccess", context -> render(context, new Fixtures()
                        .reply("/gitRepo/v1/pullFromGit", "{\"ok\": true, \"message\": \"Pulled 7 files.\"}")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Pull from Git")));
                    play.waitFor(() -> play.expect(screen.getByText("Pull Success")).toBeInTheDocument());
                    play.expect(screen.getByText("Pulled 7 files.")).toBeInTheDocument();
                    // The refresh is the alert's onClose, so it happens on dismissal, not before
                    play.expect(play.spy(REFRESH_EXPLORER)).not().toHaveBeenCalled();
                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(play.spy(REFRESH_EXPLORER)).toHaveBeenCalledTimes(1));
                    DocEditors.expectNoUnhandledRequests(play);
                })
                .story("CheckUpdatesFailure", context -> render(context, new Fixtures()
                        .reply("/gitRepo/v1/areUpdatesAvailable", "{\"ok\": false, \"message\": \"Auth failed.\"}")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", StroomDom.button("Check for updates")));
                    play.waitFor(() -> play.expect(screen.getByText("Update Check Failure")).toBeInTheDocument());
                    play.expect(screen.getByText("Auth failed.")).toBeInTheDocument();
                    DocEditors.expectNoUnhandledRequests(play);
                })
                // The credential list is fetched when the picker is opened (CredentialListModel's
                // onRangeChange), not when the editor is shown
                .story("CredentialListLoadsOnOpenNotMount", context -> render(context, new Fixtures()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getByText("Credentials", "label")).toBeInTheDocument());
                    // The document's credential is shown (fetched by name) ...
                    final Query box = play.findByDisplayValue("my-token");
                    // ... and the list hasn't been fetched
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_CREDENTIALS).toSpyMatcher());
                    // Differs from React: the box is a text box showing the credential's name
                    play.click(box);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_CREDENTIALS)
                                    .withJsonBodyContaining("{\"credentialTypes\": "
                                            + "[\"SSH_KEY\", \"USERNAME_PASSWORD\", \"ACCESS_TOKEN\"]}")
                                    .toSpyMatcher()));
                    // Differs from React: the list is a popup on the page's body
                    play.waitFor(() -> play.expect(screen.getByText("my-ssh-key")).toBeInTheDocument());
                    play.expect(screen.getByText("[ none ]")).toBeInTheDocument();
                    play.expect(countFinds(play)).toBe(1);
                    DocEditors.expectNoProblems(play);
                })
                // The credential is fetched by name, whatever the list holds
                .story("SelectedCredentialShownWithoutTheList", context -> render(context, new Fixtures()
                        .credentials(NO_CREDENTIALS)))
                .withPlay(play -> {
                    // Differs from React: the box is a text box showing the credential's name
                    play.expect(play.findByDisplayValue("my-token")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // A credential the server no longer has: getByName replies 204 (null), and the box
                // is set to null
                .story("DanglingCredentialShowsNone", context -> render(context, new Fixtures()
                        .credentials(NO_CREDENTIALS)
                        .credentialByName(RestReply.noContent())))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/getByName").toSpyMatcher()));
                    final Query label = play.findByText("Credentials", "label");
                    // Differs from React: GWT's box shows nothing for no credential; '[ none ]' is
                    // only the list's first item
                    play.waitFor(() -> play.expect(play.querySelector(StroomDom.SELECTION_BOX)).toHaveValue(""));
                    play.expect(play.queryByDisplayValue("my-token")).toBeNull();
                    play.expect(label).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // The default HTTP client configuration is fetched when the document is read, and
                // seeding it doesn't make the document dirty
                .story("HttpClientConfigSeedsWithoutDirtying", context -> render(context, new Fixtures()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(GET_DEFAULT_CONFIG).toSpyMatcher()));
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    play.click(play.getByRole("button", StroomDom.button("Set Http Client Config")));
                    screen.findByText("Edit HTTP Client Configuration");
                    // Cancel: the document is untouched, so Save is still disabled
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration")).toBeNull());
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // The seed rides along with any edit: onWrite writes the seeded configuration on
                // every save, so editing the URL is enough to save the server's default
                .story("HttpClientConfigSeedRidesAlongWithAnyEdit", context -> render(context, new Fixtures()))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(GET_DEFAULT_CONFIG).toSpyMatcher()));
                    play.type(play.getByLabelText("Git repository URL"), "/x");
                    // Differs from React: the text box reports its change when it loses the focus,
                    // and the document React records on each change is the one GWT writes when it
                    // is saved, so the play saves it and checks the request
                    play.tab();
                    final Query save = play.getByRole("button", "Save");
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(UPDATE)
                                    .withJsonBodyContaining("{\"url\": \"https://github.com/example/repo.git/x\", "
                                            + "\"httpClientConfiguration\": {\"maxConnections\": 77}}")
                                    .toSpyMatcher()));
                    DocEditors.expectNoProblems(play);
                })
                // OK writes the configuration onto the document, which makes it dirty
                .story("HttpClientConfigOkDirtiesTheDoc", context -> render(context, new Fixtures()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(GET_DEFAULT_CONFIG).toSpyMatcher()));
                    play.click(play.getByRole("button", StroomDom.button("Set Http Client Config")));
                    screen.findByText("Edit HTTP Client Configuration");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit HTTP Client Configuration")).toBeNull());
                    // Differs from React: OK with the configuration unchanged doesn't make the
                    // document dirty (onSetHttpClientConfiguration only calls onChange() when the
                    // edited configuration differs from the seeded one), so Save stays disabled
                    play.sleep(300);
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // The configuration button has no URL precondition; the remote actions do
                .story("HttpClientConfigEnabledWithoutAUrl", context -> render(context, new Fixtures().noUrl()))
                .withPlay(play -> {
                    play.expect(play.findByRole("button", StroomDom.button("Set Http Client Config")))
                            .not().toBeDisabled();
                    play.waitFor(() -> play.expect(play.getByRole("button", StroomDom.button("Pull from Git")))
                            .toBeDisabled());
                    play.expect(play.getByRole("button", StroomDom.button("Check for updates"))).toBeDisabled();
                    DocEditors.expectNoProblems(play);
                })
                // Settings, read only: the partner of Default
                .story("SettingsReadOnly", context -> render(context, new Fixtures(), true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Git repository URL", "label")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.querySelector(URL_INPUT))
                            .toHaveValue("https://github.com/example/repo.git"));
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    DocEditors.expectNoProblems(play);
                });
    }

    // The number of credential list fetches, read when the step runs
    private static Supplier<Object> countFinds(final Play play) {
        final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
        return () -> (int) requests.getCalls().stream()
                .filter(call -> String.valueOf(call.get(0)).startsWith("POST " + FIND_CREDENTIALS))
                .count();
    }

    private static Widget render(final StoryContext context, final Fixtures fixtures) {
        return render(context, fixtures, false);
    }

    private static Widget render(final StoryContext context, final Fixtures fixtures, final boolean readOnly) {
        final GitRepoResource resource = GWT.create(GitRepoResource.class);
        return DocEditors.render(context, fixtures.build(), readOnly, (harness, injector) -> {
            harness.fn(REFRESH_EXPLORER);
            harness.addRegistration(harness.getEventBus().addHandler(RefreshExplorerTreeEvent.getType(),
                    event -> harness.spy(REFRESH_EXPLORER, "refresh")));
            DocEditors.open(harness,
                    DOC_REF,
                    injector.getGitRepoPresenter(),
                    DocResource.of(
                            restFactory -> restFactory.create(resource).method(res -> res.fetch(DOC_REF.getUuid())),
                            (restFactory, doc) -> restFactory.create(resource)
                                    .method(res -> res.update(doc.getUuid(), doc))));
        });
    }

    // A story's fixtures: React's Harness props
    private static final class Fixtures {

        private final RestFixtures.Builder builder = RestFixtures.builder();
        private String url = GIT_URL;
        private String commit = "";
        private String credentials = CREDENTIALS;
        private RestReply credentialByName = RestReply.json(MY_TOKEN);

        private Fixtures commit(final String commit) {
            this.commit = "\"commit\": \"" + commit + "\",";
            return this;
        }

        private Fixtures noUrl() {
            url = "";
            return this;
        }

        private Fixtures credentials(final String credentials) {
            this.credentials = credentials;
            return this;
        }

        private Fixtures credentialByName(final RestReply reply) {
            credentialByName = reply;
            return this;
        }

        private Fixtures reply(final String path, final String json) {
            builder.post(path, RestReply.json(json));
            return this;
        }

        private RestFixtures build() {
            return DocEditors.permissionRoutes(builder)
                    .get(UPDATE, RestReply.json(DOC.replace("URL", url).replace("COMMIT", commit)))
                    .put(UPDATE, request -> RestReply.json(request.getBody()))
                    .post(GET_DEFAULT_CONFIG, RestReply.json(DEFAULT_HTTP_CLIENT_CONFIG))
                    .post(FIND_CREDENTIALS, RestReply.json(credentials))
                    .post("/credentials/getByName", credentialByName)
                    .build();
        }
    }
}
