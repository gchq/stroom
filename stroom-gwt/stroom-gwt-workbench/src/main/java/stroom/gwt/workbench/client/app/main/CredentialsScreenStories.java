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

import stroom.gwt.workbench.client.app.gin.security.SecurityScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.UploadReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories of `App/Main/CredentialsScreen`, showing Stroom's real `CredentialsController` (the
/// 'Credentials' tab) with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /credentials/findCredentialsWithPermissions` | the credentials |
/// | `GET /credentials/createDocRef` | a new credential's reference |
/// | `POST /credentials/store` (the request spy) | saving a credential |
/// | `POST /credentials/replaceSecret` (the request spy) | replacing a credential's secret |
/// | `POST /permission/doc/v1/fetchDocumentUserPermissions` | a credential's permissions |
/// | the key store file's upload (`importfile.rpc`): an upload reply, key `rk-1` | key stores |
///
/// The credentials are an editable Username / Password (`My Creds`, selected when the list loads),
/// an Access Token the user can't edit and an editable Key Store File (`File Store`, a JKS file at
/// `/etc/stroom/ks.jks`).
///
/// The presenter comes from GIN and is shown as `CredentialsPlugin` shows it.
public final class CredentialsScreenStories {

    private static final String FIND_CREDENTIALS = "/credentials/findCredentialsWithPermissions";

    // The credentials, with the user's permissions on each
    private static final String CREDENTIALS = """
            {"values": [{"credential": {"uuid": "c-1", "name": "My Creds",
                                        "credentialType": "USERNAME_PASSWORD"},
                         "edit": true, "delete": true},
                        {"credential": {"uuid": "c-2", "name": "Read Only Token",
                                        "credentialType": "ACCESS_TOKEN"},
                         "edit": false, "delete": false},
                        {"credential": {"uuid": "c-3", "name": "File Store",
                                        "credentialType": "KEY_STORE_FILE", "keyStoreType": "JKS",
                                        "keyStorePath": "/etc/stroom/ks.jks"},
                         "edit": true, "delete": true}],
             "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}}""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post(FIND_CREDENTIALS, RestReply.json(CREDENTIALS))
            // The new credential's permissions
            .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json("""
                    {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}"""))
            .get("/credentials/createDocRef", RestReply.json("""
                    {"type": "Credential", "uuid": "new-uuid", "name": ""}"""))
            .post("/credentials/store", RestReply.json("""
                    {"uuid": "new-uuid", "name": "CI Token", "credentialType": "USERNAME_PASSWORD"}"""))
            // The credential whose secret was replaced
            .post("/credentials/replaceSecret", RestReply.json("""
                    {"uuid": "c-1", "name": "My Creds", "credentialType": "USERNAME_PASSWORD"}"""))
            // The key store file's upload
            .upload(UploadReply.success("rk-1", "ks.p12"))
            .build();

    // The fixtures with a slow list, so a play can see the screen before it has loaded
    private static final RestFixtures SLOW_LIST = RestFixtures.builder()
            .post(FIND_CREDENTIALS, RestReply.json(CREDENTIALS).delayed(1000))
            .build()
            .followedBy(FIXTURES);

    // The fixtures with a key store's upload refused by the server
    private static final RestFixtures UPLOAD_FAILS = RestFixtures.builder()
            .upload(UploadReply.failure("The file is not a key store"))
            .build()
            .followedBy(FIXTURES);

    // The fixtures with a key store credential in the list
    private static final RestFixtures KEY_STORE_LIST = RestFixtures.builder()
            .post(FIND_CREDENTIALS, RestReply.json("""
                    {"values": [{"credential": {"uuid": "c-4", "name": "TLS Store",
                                                "credentialType": "KEY_STORE", "keyStoreType": "JKS"},
                                 "edit": true, "delete": true}],
                     "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""))
            .build()
            .followedBy(FIXTURES);

    private static final String REPLACE_SECRET_TITLE = "Replace Selected Credential's Secret";
    private static final String SECRET_HIDDEN =
            "The secret is set but cannot be shown. Use Replace Secret to set a new one.";
    private static final String ROW = StroomDom.GRID_ROW;

    private CredentialsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/CredentialsScreen", CredentialsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Add: New Credentials, a user name and password, stored as a usernamePassword secret
                .story("Credentials", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    play.expect(play.getByText("Username / Password")).toBeInTheDocument();
                    final Play dialog = openNewCredentials(play);
                    // Expires is shown but disabled until 'Credentials expire' is ticked (it was
                    // hidden, so the dialog changed shape)
                    final Query expires = dialog.getByLabelText("Expires");
                    play.expect(expires).toBeVisible();
                    play.expect(expires).toBeDisabled();
                    play.click(dialog.getByRole("checkbox", "Credentials expire"));
                    play.waitFor(() -> play.expect(expires).toBeEnabled());
                    play.click(dialog.getByRole("checkbox", "Credentials expire"));
                    play.type(dialog.getByLabelText("Name"), "CI Token");
                    play.type(dialog.getByLabelText("User Name"), "bob");
                    play.type(dialog.getByLabelText("Password"), "s3cret");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store")
                                    .withJsonBodyContaining("""
                                            {"credential": {"name": "CI Token"},
                                             "secret": {"type": "usernamePassword", "username": "bob"}}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Choosing SSH Key swaps in the private key fields
                .story("SshKeyType", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    // GWT's SelectionBox opens when its text box is clicked
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(play.screen().findByText("SSH Key"));
                    dialog.findByLabelText("Private Key");
                    play.expect(dialog.getByText("Verify Hosts")).toBeInTheDocument();
                    SecurityPlays.expectNoProblems(play);
                })
                // Key Store: the chosen file is uploaded, and the stored keyStore secret carries the
                // upload's resource key and the key store type (the server takes the type from the
                // secret, so the credential itself leaves it out)
                .story("KeyStore", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "TLS Store");
                    // GWT's SelectionBox opens when its text box is clicked
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(play.screen().findByText("Key Store"));
                    play.waitFor(() -> play.expect(dialog.querySelector(StroomDom.FILE_INPUT)).not().toBeNull());
                    play.upload(dialog.querySelector(StroomDom.FILE_INPUT), "ks.p12", "x", "application/octet-stream");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.UPLOAD_SPY))
                            .toHaveBeenCalledWith("importfile.rpc", "ks.p12", "x"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store")
                                    .withJsonBodyContaining("""
                                            {"credential": {"name": "TLS Store"},
                                             "secret": {"type": "keyStore", "keyStoreType": "PKCS12",
                                                        "resourceKey": {"key": "rk-1"}}}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Key Store File: the type and path (not secret) are stored in a keyStoreFile secret
                .story("KeyStoreFile", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "Managed Store");
                    chooseType(play, dialog, "Key Store File");
                    play.type(dialog.findByLabelText("Key Store File Path"), "/etc/stroom/managed.p12");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store")
                                    .withJsonBodyContaining("""
                                            {"credential": {"name": "Managed Store",
                                                            "credentialType": "KEY_STORE_FILE"},
                                             "secret": {"type": "keyStoreFile", "keyStoreType": "PKCS12",
                                                        "keyStorePath": "/etc/stroom/managed.p12"}}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Key Store File with no path: OK alerts and stores nothing
                .story("KeyStoreFileNoPath", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "Managed Store");
                    chooseType(play, dialog, "Key Store File");
                    dialog.findByLabelText("Key Store File Path");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: You must enter the path of the key store file"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A user name and password, the default type, both empty: OK says what is missing, and
                // stores nothing
                .story("UsernamePasswordRequired", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "Service Account");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: Username must not be empty"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A key store with no file chosen: OK says so, and uploads and stores nothing
                .story("KeyStoreNoFile", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "TLS Store");
                    chooseType(play, dialog, "Key Store");
                    play.waitFor(() -> play.expect(dialog.querySelector(StroomDom.FILE_INPUT)).not().toBeNull());
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: You must select a file to import"));
                    play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store").toSpyMatcher());
                })
                // Access Token: the token is stored in an accessToken secret
                .story("AccessToken", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "Git Token");
                    chooseType(play, dialog, "Access Token");
                    play.type(dialog.findByLabelText("Access Token"), "ghp_abc");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store")
                                    .withJsonBodyContaining("""
                                            {"credential": {"name": "Git Token"},
                                             "secret": {"type": "accessToken", "accessToken": "ghp_abc"}}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Access Token with no token: OK says so, and stores nothing
                .story("AccessTokenRequired", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "Git Token");
                    chooseType(play, dialog, "Access Token");
                    dialog.findByLabelText("Access Token");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: Access token must not be empty"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // SSH Key: the private key is stored in an sshKey secret, verifying hosts by default
                .story("SshKey", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "Deploy Key");
                    chooseType(play, dialog, "SSH Key");
                    play.type(dialog.findByLabelText("Private Key"), "KEY");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store")
                                    .withJsonBodyContaining("""
                                            {"credential": {"name": "Deploy Key"},
                                             "secret": {"type": "sshKey", "privateKey": "KEY",
                                                        "verifyHosts": true}}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // SSH Key with no private key: OK says so, and stores nothing
                .story("SshKeyRequired", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "Deploy Key");
                    chooseType(play, dialog, "SSH Key");
                    dialog.findByLabelText("Private Key");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: Private key must not be empty"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A key store whose upload the server refuses: its reason is shown, nothing is stored
                // and the dialog stays open
                .story("KeyStoreUploadFails", context -> render(context, UPLOAD_FAILS, false))
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "TLS Store");
                    chooseType(play, dialog, "Key Store");
                    play.waitFor(() -> play.expect(dialog.querySelector(StroomDom.FILE_INPUT)).not().toBeNull());
                    play.upload(dialog.querySelector(StroomDom.FILE_INPUT), "ks.p12", "x", "application/octet-stream");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: The file is not a key store"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store").toSpyMatcher());
                    play.expect(play.screen().queryByText("New Credentials")).not().toBeNull();
                })
                // Replace Secret is live only for a selected credential the user can edit; it asks for
                // a whole new secret of the credential's type and sends it with the credential's uuid
                .story("ReplaceSecret", context -> render(context, SLOW_LIST, false))
                .withPlay(play -> {
                    // Nothing is selected while the list loads (it is slow here). The list is
                    // single-select, so once loaded a selection can't be cleared
                    final Query replace = play.findByRole("button", TextMatch.containing(REPLACE_SECRET_TITLE));
                    play.expect(play.queryByText("My Creds")).toBeNull();
                    play.expect(replace).toHaveAttribute("aria-disabled", "true");
                    // The first credential is selected when the list loads
                    play.waitFor(3000, () -> play.expect(play.getByText("My Creds").closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    play.expect(replace).not().toHaveAttribute("aria-disabled", "true");
                    // A credential the user can't edit
                    play.click(play.getByText("Read Only Token"));
                    play.waitFor(() -> play.expect(play.getByText("Read Only Token").closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    play.expect(replace).toHaveAttribute("aria-disabled", "true");
                    // An editable one
                    play.click(play.getByText("My Creds"));
                    play.waitFor(() -> play.expect(replace).not().toHaveAttribute("aria-disabled", "true"));
                    play.click(replace);
                    final Play screen = play.screen();
                    final Play dialog = screen.within(screen.findByText("Replace Username / Password Secret")
                            .closest(StroomDom.DIALOG));
                    play.expect(dialog.getByText(TextMatch.startingWith(
                            "The current secret of 'My Creds' will be discarded"))).toBeInTheDocument();
                    // The secret's first field has the focus
                    play.waitFor(() -> play.expect(dialog.getByLabelText("User Name")).toHaveFocus());
                    play.type(dialog.getByLabelText("User Name"), "alice");
                    play.type(dialog.getByLabelText("Password"), "n3w");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/replaceSecret")
                                    .withJsonBodyContaining("""
                                            {"uuid": "c-1",
                                             "secret": {"type": "usernamePassword", "username": "alice",
                                                        "password": "n3w"}}""")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Replace Username / Password Secret"))
                            .toBeNull());
                    SecurityPlays.expectNoProblems(play);
                })
                // Replacing a Key Store File's secret starts from its type and path, which aren't secret
                .story("ReplaceKeyStoreFileSecret", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.click(play.findByText("File Store"));
                    final Query replace = play.getByRole("button", TextMatch.containing(REPLACE_SECRET_TITLE));
                    play.waitFor(() -> play.expect(play.getByText("File Store").closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    play.expect(replace).not().toHaveAttribute("aria-disabled", "true");
                    play.click(replace);
                    final Play screen = play.screen();
                    final Play dialog = screen.within(screen.findByText("Replace Key Store File Secret")
                            .closest(StroomDom.DIALOG));
                    play.expect(dialog.getByLabelText("Key Store Type")).toHaveValue("JKS");
                    play.expect(dialog.getByLabelText("Key Store File Path")).toHaveValue("/etc/stroom/ks.jks");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/replaceSecret")
                                    .withJsonBodyContaining("""
                                            {"uuid": "c-3",
                                             "secret": {"type": "keyStoreFile", "keyStoreType": "JKS",
                                                        "keyStorePath": "/etc/stroom/ks.jks"}}""")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Replace Key Store File Secret"))
                            .toBeNull());
                    SecurityPlays.expectNoProblems(play);
                })
                // Replacing a Key Store's secret starts from its type, uploads the new file and sends
                // the upload's resource key
                .story("ReplaceKeyStoreSecret", context -> render(context, KEY_STORE_LIST, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("TLS Store").closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    final Query replace = play.getByRole("button", TextMatch.containing(REPLACE_SECRET_TITLE));
                    play.waitFor(() -> play.expect(replace).not().toHaveAttribute("aria-disabled", "true"));
                    play.click(replace);
                    final Play screen = play.screen();
                    final Play dialog = screen.within(screen.findByText("Replace Key Store Secret")
                            .closest(StroomDom.DIALOG));
                    play.expect(dialog.getByLabelText("Key Store Type")).toHaveValue("JKS");
                    play.waitFor(() -> play.expect(dialog.querySelector(StroomDom.FILE_INPUT)).not().toBeNull());
                    play.upload(dialog.querySelector(StroomDom.FILE_INPUT), "new.jks", "y", "application/octet-stream");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.UPLOAD_SPY))
                            .toHaveBeenCalledWith("importfile.rpc", "new.jks", "y"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/replaceSecret")
                                    .withJsonBodyContaining("""
                                            {"uuid": "c-4",
                                             "secret": {"type": "keyStore", "keyStoreType": "JKS",
                                                        "resourceKey": {"key": "rk-1"}}}""")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Replace Key Store Secret")).toBeNull());
                    SecurityPlays.expectNoProblems(play);
                })
                // Editing an existing credential never shows its secret: the type is fixed, the secret
                // fields are replaced by a note, and a rename (confirmed) stores no secret
                .story("EditHidesSecret", context -> render(context, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // A Key Store File's path isn't secret, so it is shown, read only
                    play.click(play.findByText("File Store"));
                    play.waitFor(() -> play.expect(play.getByText("File Store").closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    final Play fileDialog = openEditCredentials(play);
                    play.expect(fileDialog.getByLabelText("Type")).toBeDisabled();
                    play.expect(fileDialog.getByText(SECRET_HIDDEN)).toBeVisible();
                    play.expect(fileDialog.getByText("/etc/stroom/ks.jks")).toBeVisible();
                    play.expect(fileDialog.queryByRole("textbox", "Key Store File Path")).toBeNull();
                    play.click(fileDialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Credentials")).toBeNull());

                    play.click(play.getByText("My Creds"));
                    play.waitFor(() -> play.expect(play.getByText("My Creds").closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    final Play dialog = openEditCredentials(play);
                    play.expect(dialog.getByLabelText("Type")).toBeDisabled();
                    play.expect(dialog.getByText(SECRET_HIDDEN)).toBeVisible();
                    // A new credential of this type would show these
                    play.expect(dialog.queryByLabelText("User Name")).toBeNull();
                    play.expect(dialog.queryByLabelText("Password")).toBeNull();
                    // No path to show for a Username / Password
                    play.expect(dialog.getByText("Key Store File Path")).not().toBeVisible();
                    play.clear(dialog.getByLabelText("Name"));
                    play.type(dialog.getByLabelText("Name"), "Renamed Creds");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    final Play confirm = screen.within(screen.findByText(
                                    TextMatch.containing("Anything that refers to 'My Creds' will stop working"))
                            .closest(StroomDom.DIALOG));
                    play.expect(play.spy(ScreenHarness.CONFIRM_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("refer to credentials by name"));
                    // Nothing is stored until the rename is confirmed
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store").toSpyMatcher());
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/store")
                                    .withJsonBodyContaining("""
                                            {"credential": {"uuid": "c-1", "name": "Renamed Creds",
                                                            "credentialType": "USERNAME_PASSWORD"}}""")
                                    .withBody("has no secret", CredentialsScreenStories::hasNoSecret)
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Credentials")).toBeNull());
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// Chooses a credential type in the dialog's Type box.
    private static void chooseType(final Play play, final Play dialog, final String type) {
        // GWT's SelectionBox opens when its text box is clicked
        play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
        play.click(play.screen().findByRole("option", type));
    }

    /// Opens the 'Edit Credentials' dialog for the selected credential.
    ///
    /// @return The dialog.
    private static Play openEditCredentials(final Play play) {
        final Query edit = play.getByRole("button", TextMatch.containing("Edit Selected Credential"));
        play.waitFor(() -> play.expect(edit).not().toHaveAttribute("aria-disabled", "true"));
        play.click(edit);
        final Play screen = play.screen();
        return screen.within(screen.findByText("Edit Credentials").closest(StroomDom.DIALOG));
    }

    private static boolean hasNoSecret(final String body) {
        final Object json = body == null
                ? null
                : JsonValues.parse(body);
        return json instanceof final Map<?, ?> map && map.get("secret") == null;
    }

    /// Opens the 'New Credentials' dialog with the list's New Credential button.
    ///
    /// @return The dialog.
    private static Play openNewCredentials(final Play play) {
        play.click(play.getByTitle("New Credential"));
        // Stroom's dialogs have no role="dialog"
        final Play screen = play.screen();
        return screen.within(screen.findByText("New Credentials").closest(StroomDom.DIALOG));
    }

    private static Widget render(final StoryContext context) {
        return render(context, FIXTURES, false);
    }

    private static Widget render(final StoryContext context, final boolean realAlerts) {
        return render(context, FIXTURES, realAlerts);
    }

    /// @param fixtures   The story's fixtures.
    /// @param realAlerts True to show Stroom's real alert and confirmation dialogs, so a play can
    ///                   answer them.
    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final boolean realAlerts) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .injector(injector);
        if (realAlerts) {
            builder.realAlerts();
        }
        final ScreenHarness harness = builder.build();
        // Shown as CredentialsPlugin shows it, once Stroom has started
        harness.afterStartUp(() -> harness.show(injector.getCredentialsController()));
        return harness.asWidget();
    }
}
