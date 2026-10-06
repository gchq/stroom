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

import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter;
import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/CredentialPickerDialog` in the React Storybook, showing Stroom's
/// real [CredentialsManagerDialogPresenter] (the 'Credentials' picker) with fake REST replies.
///
/// The presenter comes from GIN ([AppScreenGinjector]), as Stroom injects it into
/// `ContentStoreContentPackDetailsPresenter`, and the story shows it as that presenter does. The
/// React story's `loadCredentials` fixture becomes a route for
/// `POST /credentials/findCredentialsWithPermissions` and its `onPick` callback a spy called from
/// the dialog's OK handler.
public final class CredentialPickerDialogStories {

    /// The name of the spy recording the credential picked.
    static final String ON_PICK = "onPick";

    // CredentialsResource.findCredentialsWithPermissions(), with the React fixture's credentials
    // (shaped as the gwt-suite corpus's reply)
    private static final String CREDENTIALS = """
            {
              "values": [
                {
                  "credential": {"uuid": "c1", "name": "github-token", "credentialType": "ACCESS_TOKEN"},
                  "edit": true,
                  "delete": true
                },
                {
                  "credential": {"uuid": "c2", "name": "gitlab-ssh", "credentialType": "SSH_KEY"},
                  "edit": true,
                  "delete": true
                }
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }
            """;

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/credentials/findCredentialsWithPermissions", RestReply.json(CREDENTIALS))
            .build();

    private CredentialPickerDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/CredentialPickerDialog", CredentialPickerDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Pick a stored credential -> returns its name (GWT CredentialsManagerDialog)
                .story("Pick", CredentialPickerDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("github-token")).toBeInTheDocument());
                    play.click(screen.getByText("gitlab-ssh"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    // Differs from React: the React picker returns the credential's name; GWT's
                    // getCredentialName() returns the selected credential's UUID (its view's
                    // getSelectedCredentialsId()), which ContentStore passes on as the name
                    play.waitFor(() -> play.expect(play.spy(ON_PICK)).toHaveBeenCalledWith("c2"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/credentials/findCredentialsWithPermissions")
                                    .withJsonBodyContaining("{\"requiredPermission\": \"VIEW\"}")
                                    .toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.fn(ON_PICK);

        // Shown as ContentStoreContentPackDetailsPresenter shows it, with no help text and nothing
        // selected
        final CredentialsManagerDialogPresenter dialog = injector.getCredentialsManagerDialogPresenter();
        final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(dialog);
        dialog.setupDialog(builder, null, null);
        builder.onHideRequest(event -> {
            if (event.isOk()) {
                harness.spy(ON_PICK, dialog.getCredentialName());
            }
            event.hide();
        });
        builder.fire();
        return harness.asWidget();
    }
}
