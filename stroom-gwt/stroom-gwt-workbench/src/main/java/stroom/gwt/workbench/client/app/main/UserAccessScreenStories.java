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
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.client.presenter.UserAccessPresenter;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/UserAccessScreen` in the React Storybook, showing Stroom's real
/// [UserAccessPresenter] (the 'User Access' tab: users with sessions or tokens above, the selected
/// user's sessions below) with fake REST replies.
///
/// | React seam | Stroom REST endpoint |
/// |---|---|
/// | `find` | `POST /userAccess/v1/find` |
/// | `listSessions` | `POST /userAccess/v1/sessions?subjectId=...` |
/// | `revoke` | `POST /userAccess/v1/revoke?subjectId=...` |
/// | `terminateSession` | `POST /session/v1/terminateSession?sessionHandle=...&nodeName=...` |
/// | React's internal/external IdP UI config | the extended UI config's `externalIdentityProvider` |
///
/// React's recorder of the calls becomes checks on the request spy, and `openScreen` a spy on
/// Stroom's `OpenUserEvent`. The user holds `MANAGE_USERS_PERMISSION`, as React's fixture.
/// Confirmations are Stroom's real dialogs. The presenter comes from GIN and is opened as
/// `UserAccessPlugin` opens it (refreshed).
public final class UserAccessScreenStories {

    /// The name of the spy recording the users opened with 'Open this user'.
    static final String OPEN_SCREEN = "openScreen";

    private static final String REVOKE_TITLE = "End this user's sessions and revoke their tokens";
    private static final String OPEN_USER_TITLE = "Open this user, where they can be disabled";

    // UserAccessResource.find(): the second row is a subject with no stroom user
    private static final String ROWS = """
            {"values": [
              {"displayName": "Alice Smith", "subjectId": "alice", "sessionCount": 2, "tokenCount": 3,
               "nodeNames": ["node1", "node2"], "lastAccessedMs": 1700000000000,
               "lastTokenExpiryMs": 1800000000000,
               "userRef": {"uuid": "u-alice", "subjectId": "alice", "displayName": "Alice Smith",
                           "group": false, "enabled": true}},
              {"displayName": "orphan.subject", "subjectId": "orphan.subject", "sessionCount": 1,
               "tokenCount": 0, "nodeNames": ["node1"], "lastAccessedMs": 1700000000000}
            ], "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    // UserAccessResource.listSessions()
    private static final String SESSIONS = """
            [{"sessionHandle": "s-1", "nodeName": "node1", "createMs": 1700000000000,
              "lastAccessedMs": 1700000500000, "lastAccessedAgent": "Mozilla/5.0 (X11; Linux x86_64)"},
             {"sessionHandle": "s-2", "nodeName": "node2", "createMs": 1700000100000,
              "lastAccessedMs": 1700000600000, "lastAccessedAgent": "curl/8.5.0"}]""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/userAccess/v1/find", RestReply.json(ROWS))
            .post("/userAccess/v1/sessions", RestReply.json(SESSIONS))
            .post("/userAccess/v1/revoke", RestReply.json("3"))
            .post("/session/v1/terminateSession", RestReply.json("true"))
            .build();

    private UserAccessScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/UserAccessScreen", UserAccessScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The grid has UserAccessListPresenter's seven columns
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    // Two grids are shown, both with a 'Last Accessed' column, so scope to the users'
                    final Play users = usersGrid(play);
                    for (final String heading : new String[]{"User", "Subject Id", "Sessions", "Nodes",
                            "Last Accessed", "Tokens", "Access Expires"}) {
                        play.expect(users.getByRole("columnheader", heading)).toBeInTheDocument();
                    }
                    // Node names are joined
                    play.expect(play.getByText("node1, node2")).toBeInTheDocument();
                    // No column is sortable
                    play.expect(play.querySelectorAll("." + StroomDom.SORTABLE_HEADER)).toHaveLength(0);
                    // The dates carry a relative suffix (formatWithDuration).
                    // Differs from React: Moment.js says 'in 3 years', not '3 years from now'
                    play.expect(users.getAllByText(TextMatch.regex("\\((.* ago|in .*)\\)$")).count())
                            .toBeGreaterThan(1);
                    SecurityPlays.expectNoProblems(play);
                })
                // Multi-select, and a split pane between the two grids
                .story("MultiSelectAndSplitPane", context -> render(context, false))
                .withPlay(play -> {
                    play.click(play.findByText("Alice Smith"));
                    // Selection is applied on mousedown; a held modifier is given to fireEvent
                    play.fireEvent().mouseDown(play.getAllByText("orphan.subject").nth(0),
                            EventInit.create().ctrlKey());
                    final Play users = usersGrid(play);
                    play.waitFor(() -> play.expect(users.querySelectorAll("." + StroomDom.SELECTED_ROW))
                            .toHaveLength(2));
                    // The two panes are split, so the divider can be dragged
                    play.expect(play.querySelector(".thinSplitLayoutPanel-VDragger")).toBeInTheDocument();
                    SecurityPlays.expectNoProblems(play);
                })
                // Selecting a user loads their sessions below
                .story("SelectionDrivesTheSessionList", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    // Differs from React: GWT selects the first user when the list loads
                    // (UserAccessListPresenter.changeData), so Alice's sessions are shown at once rather
                    // than an empty list; and GWT has no 'Sessions for Alice Smith' heading. The sessions
                    // are fetched for the selected subject
                    play.expect(play.within(play.getByText("Alice Smith").closest("tr")).getByText("alice"))
                            .toBeInTheDocument();
                    play.expect(play.getByText("Alice Smith").closest("tr")).toHaveClass(StroomDom.SELECTED_ROW);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userAccess/v1/sessions").withQuery("subjectId=alice")
                                    .toSpyMatcher()));
                    play.expect(sessionsGrid(play).findByText("curl/8.5.0")).toBeInTheDocument();
                    // Selecting another user loads theirs
                    play.click(play.getAllByText("orphan.subject").nth(0));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userAccess/v1/sessions").withQuery("subjectId=orphan.subject")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Both buttons are dead without a selection
                .story("ButtonsGatedOnSelection", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    final Query revoke = play.getByRole("button", TextMatch.containing(REVOKE_TITLE));
                    final Query openUser = play.getByRole("button", OPEN_USER_TITLE);
                    // Differs from React: GWT selects the first user when the list loads, so both
                    // buttons start live; with the selection cleared (a ctrl-click on the selected
                    // row) they go dead
                    play.expect(revoke).not().toHaveClass("disabled");
                    play.fireEvent().mouseDown(play.getByText("Alice Smith"), EventInit.create().ctrlKey());
                    play.waitFor(() -> play.expect(usersGrid(play).querySelectorAll("." + StroomDom.SELECTED_ROW))
                            .toHaveLength(0));
                    play.expect(revoke).toHaveClass("disabled");
                    play.expect(openUser).toHaveClass("disabled");
                    SecurityPlays.expectNoProblems(play);
                })
                // A row with no stroom user can't be opened, but can be revoked
                .story("OpenUserNeedsAStroomUser", context -> render(context, false))
                .withPlay(play -> {
                    play.click(play.findAllByText("orphan.subject").nth(0));
                    play.expect(play.getByRole("button", OPEN_USER_TITLE)).toHaveClass("disabled");
                    play.expect(play.getByRole("button", TextMatch.containing(REVOKE_TITLE)))
                            .not().toHaveClass("disabled");
                    SecurityPlays.expectNoProblems(play);
                })
                // The internal IdP's confirmation: tokens stop at once, the account stays enabled
                .story("RevokeConfirmInternalIdp", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Alice Smith"));
                    play.click(play.getByRole("button", TextMatch.containing(REVOKE_TITLE)));
                    screen.findByText(TextMatch.containing(
                            "End all sessions and revoke all tokens for 'Alice Smith'?"));
                    play.expect(screen.getByText(TextMatch.containing("revoke the 3 token(s) issued to them")))
                            .toBeInTheDocument();
                    play.expect(screen.getByText(TextMatch.containing("Their account stays enabled")))
                            .toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userAccess/v1/revoke").withQuery("subjectId=alice")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // An external IdP's confirmation: only the sessions can be ended
                .story("RevokeConfirmExternalIdp", context -> render(context, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Alice Smith"));
                    play.click(play.getByRole("button", TextMatch.containing(REVOKE_TITLE)));
                    screen.findByText(TextMatch.containing(
                            "uses an external identity provider, so their tokens cannot be revoked here"));
                    play.expect(screen.getByText(TextMatch.containing(
                                    "This is therefore a forced re-authentication, not a way to lock someone out")))
                            .toBeInTheDocument();
                    // The internal IdP's promise isn't made
                    play.expect(screen.queryByText(TextMatch.containing("stops working at once"))).toBeNull();
                    SecurityPlays.expectNoProblems(play);
                })
                // Ending one session names the node
                .story("EndOneSession", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Alice Smith"));
                    play.click(play.findByText("curl/8.5.0"));
                    play.click(play.getByRole("button", "End this session"));
                    screen.findByText(TextMatch.containing("end this session for 'Alice Smith' on node 'node2'?"));
                    play.expect(screen.getByText(TextMatch.containing("their other sessions will be left alone")))
                            .toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/session/v1/terminateSession")
                                    .withQuery("sessionHandle=s-2&nodeName=node2")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // The quick filter goes to the server
                .story("FilterIsServerSide", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    // Differs from React: the quick filter has no 'Filter' label, only a placeholder
                    play.type(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "ali");
                    play.waitFor(3000, () -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userAccess/v1/find")
                                    .withJsonBodyContaining("{\"filter\": \"ali\"}")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// Differs from React: GWT's grids have no role="grid", so each is found as the pager view
    /// holding its toolbar button.
    ///
    /// @return The users grid (with its toolbar and pager).
    private static Play usersGrid(final Play play) {
        return play.within(play.getByTitle(OPEN_USER_TITLE).closest(".dock-container-vertical"));
    }

    /// @return The selected user's sessions grid (with its toolbar and pager).
    private static Play sessionsGrid(final Play play) {
        return play.within(play.getByTitle("End this session").closest(".dock-container-vertical"));
    }

    private static Widget render(final StoryContext context, final boolean externalIdp) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION)
                .startup(startup -> {
                    if (externalIdp) {
                        startup.extendedUiConfig(externalIdpConfig());
                    }
                })
                .realAlerts()
                .build();
        harness.fn(OPEN_SCREEN);
        harness.getEventBus().addHandler(OpenUserEvent.getType(), event ->
                harness.spy(OPEN_SCREEN, "User: " + event.getUserRef().getDisplayName()));

        // Opened as UserAccessPlugin opens it (refreshed), once Stroom has started (it reads the
        // UI config for the identity provider)
        harness.afterStartUp(() -> {
            final UserAccessPresenter presenter = harness.addContent(injector.getUserAccessPresenter());
            presenter.refresh();
        });
        return harness.asWidget();
    }

    /// @return The default extended UI config with an external identity provider.
    private static String externalIdpConfig() {
        return "{\"uiConfig\":" + StartupFixtures.DEFAULT_UI_CONFIG
                + ",\"externalIdentityProvider\":true,\"dependencyWarningsEnabled\":false"
                + ",\"maxApiKeyExpiryAgeMs\":31536000000,\"obfuscatedFields\":[]}";
    }
}
