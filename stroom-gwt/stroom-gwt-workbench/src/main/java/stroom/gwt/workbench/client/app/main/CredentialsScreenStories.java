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
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.UploadReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/CredentialsScreen` in the React Storybook, showing Stroom's real
/// `CredentialsPresenter` (the 'Credentials' tab) with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `find` | `POST /credentials/findCredentialsWithPermissions` |
/// | `createDocRef` | `GET /credentials/createDocRef` |
/// | `store` (its recorder) | `POST /credentials/store` (the request spy) |
/// | `docPermission.fetchPermissions` | `POST /permission/doc/v1/fetchDocumentUserPermissions` |
/// | `uploadFile` | the key store file's upload (`importfile.rpc`): an upload reply, key `rk-1` |
///
/// The presenter comes from GIN and is shown as `CredentialsPlugin` shows it.
public final class CredentialsScreenStories {

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/credentials/findCredentialsWithPermissions", RestReply.json("""
                    {"values": [{"credential": {"uuid": "c-1", "name": "My Creds",
                                                "credentialType": "USERNAME_PASSWORD"},
                                 "edit": true, "delete": true}],
                     "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""))
            // The new credential's permissions (React's docPermission.fetchPermissions)
            .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json("""
                    {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}"""))
            .get("/credentials/createDocRef", RestReply.json("""
                    {"type": "Credential", "uuid": "new-uuid", "name": ""}"""))
            .post("/credentials/store", RestReply.json("""
                    {"uuid": "new-uuid", "name": "CI Token", "credentialType": "USERNAME_PASSWORD"}"""))
            // React's KeyStore uploadFile
            .upload(UploadReply.success("rk-1", "ks.p12"))
            .build();

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
                    play.type(dialog.getByLabelText("Name"), "CI Token");
                    play.type(dialog.getByLabelText("User Name"), "bob");
                    // Differs from React: the 'Password' label is for the field's container, so the
                    // field is found by its class
                    play.type(dialog.querySelector(".confirmPasswordTextBox"), "s3cret");
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
                    // Differs from React: GWT's SelectionBox opens when its text box is clicked
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(play.screen().findByText("SSH Key"));
                    dialog.findByLabelText("Private Key");
                    play.expect(dialog.getByText("Verify Hosts")).toBeInTheDocument();
                    SecurityPlays.expectNoProblems(play);
                })
                // Key Store: the chosen file is uploaded, and the stored keyStore secret carries the
                // upload's resource key
                .story("KeyStore", CredentialsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My Creds");
                    final Play dialog = openNewCredentials(play);
                    play.type(dialog.getByLabelText("Name"), "TLS Store");
                    // Differs from React: GWT's SelectionBox opens when its text box is clicked
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
                                            {"credential": {"name": "TLS Store", "keyStoreType": "PKCS12"},
                                             "secret": {"type": "keyStore", "resourceKey": {"key": "rk-1"}}}""")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// Opens the 'New Credentials' dialog with the list's Add button.
    ///
    /// @return The dialog.
    private static Play openNewCredentials(final Play play) {
        play.click(play.getByTitle("Add"));
        // Differs from React: Stroom's dialogs have no role="dialog"
        final Play screen = play.screen();
        return screen.within(screen.findByText("New Credentials").closest(StroomDom.DIALOG));
    }

    private static Widget render(final StoryContext context) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        // Shown as CredentialsPlugin shows it, once Stroom has started
        harness.afterStartUp(() -> harness.addContent(injector.getCredentialsPresenter()));
        return harness.asWidget();
    }
}
