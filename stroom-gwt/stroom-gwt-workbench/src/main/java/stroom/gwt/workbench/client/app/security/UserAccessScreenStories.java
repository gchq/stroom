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

package stroom.gwt.workbench.client.app.security;

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
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.component.UserAccessController;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/UserAccessScreen`, showing Stroom's real [UserAccessController] (the 'User
/// Access' tab: users with sessions or tokens above, the selected user's sessions below) with fake
/// REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /userAccess/v1/find` | the users |
/// | `POST /userAccess/v1/sessions?subjectId=...` | a user's sessions |
/// | `POST /userAccess/v1/revoke?subjectId=...` | revoking a user's access |
/// | `POST /session/v1/terminateSession?sessionHandle=...&nodeName=...` | terminating a session |
/// | the extended UI config's `externalIdentityProvider` | an internal or external IdP |
///
/// The calls are checked on the request spy, and the `openScreen` spy records Stroom's
/// `OpenUserEvent`. The user holds `MANAGE_USERS_PERMISSION`.
/// Confirmations are Stroom's real dialogs. The controller comes from GIN and is opened as
/// `UserAccessPlugin` opens it (refreshed).
public final class UserAccessScreenStories {

    /// The name of the spy recording the users opened with 'Open this user'.
    static final String OPEN_SCREEN = "openScreen";

    private static final String REVOKE_TITLE = "End Sessions and Revoke Tokens for Selected User";
    private static final String OPEN_USER_TITLE = "Open Selected User (to disable them)";

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

    // The second find (a refresh) has orphan.subject on another node as well
    private static final RestFixtures REFRESHED_FIXTURES = RestFixtures.builder()
            .post("/userAccess/v1/find", RestReply.json(ROWS),
                    RestReply.json(ROWS.replace("\"nodeNames\": [\"node1\"]", "\"nodeNames\": [\"node1\", \"node3\"]")))
            .build()
            .followedBy(FIXTURES);

    // Alice's access revoked, so she is no longer listed
    private static final String ROWS_WITHOUT_ALICE = """
            {"values": [
              {"displayName": "orphan.subject", "subjectId": "orphan.subject", "sessionCount": 1,
               "tokenCount": 0, "nodeNames": ["node1"], "lastAccessedMs": 1700000000000}
            ], "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    private static final RestFixtures REVOKED_FIXTURES = RestFixtures.builder()
            .post("/userAccess/v1/find", RestReply.json(ROWS), RestReply.json(ROWS_WITHOUT_ALICE))
            .build()
            .followedBy(FIXTURES);

    // The second fetch of Alice's sessions, once s-2 (node2) is ended
    private static final RestFixtures ENDED_FIXTURES = RestFixtures.builder()
            .post("/userAccess/v1/sessions", RestReply.json(SESSIONS), RestReply.json("""
                    [{"sessionHandle": "s-1", "nodeName": "node1", "createMs": 1700000000000,
                      "lastAccessedMs": 1700000500000, "lastAccessedAgent": "Mozilla/5.0 (X11; Linux x86_64)"}]"""))
            .build()
            .followedBy(FIXTURES);

    // A filter that finds nobody
    private static final RestFixtures NOBODY_FIXTURES = RestFixtures.builder()
            .post("/userAccess/v1/find", request -> request.getBody() != null && request.getBody().contains("nobody")
                    ? RestReply.json("{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, "
                                     + "\"total\": 0, \"exact\": true}}")
                    : RestReply.json(ROWS))
            .build()
            .followedBy(FIXTURES);

    private UserAccessScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/UserAccessScreen", UserAccessScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The grid has UserAccessList's seven columns
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
                    // Moment.js says e.g. 'in 3 years' or '2 days ago'
                    play.expect(users.getAllByText(TextMatch.regex("\\((.* ago|in .*)\\)$")).count())
                            .toBeGreaterThan(1);
                    SecurityPlays.expectNoProblems(play);
                })
                // One user at a time, as every action is on one, and a split pane between the two grids
                .story("OneUserAtATimeAndSplitPane", context -> render(context, false))
                .withPlay(play -> {
                    play.click(play.findByText("Alice Smith"));
                    // Selection is applied on mousedown; a held modifier is given to fireEvent. Ctrl
                    // chooses the other user in place of the first
                    play.fireEvent().mouseDown(play.getAllByText("orphan.subject").nth(0),
                            EventInit.create().ctrlKey());
                    final Play users = usersGrid(play);
                    play.waitFor(() -> play.expect(users.getAllByText("orphan.subject").nth(0)
                            .closest(StroomDom.GRID_ROW)).toHaveAttribute("aria-selected", "true"));
                    play.expect(users.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    // The two panes are split, by a splitter that can be dragged or moved from the
                    // keyboard, named by the pane it resizes
                    play.expect(play.getByRole("separator", "Sessions"))
                            .toHaveAttribute("aria-orientation", "horizontal");
                    SecurityPlays.expectNoProblems(play);
                })
                // Selecting a user loads their sessions below
                .story("SelectionDrivesTheSessionList", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    // GWT selects the first user when the list loads
                    // (UserAccessListController.changeData), so Alice's sessions are shown at once. The
                    // sessions are fetched for the selected subject
                    final Query aliceRow = play.getByText("Alice Smith").closest(StroomDom.GRID_ROW);
                    play.expect(play.within(aliceRow).getByText("alice")).toBeInTheDocument();
                    play.expect(aliceRow).toHaveClass(StroomDom.SELECTED_ROW);
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
                .story("ButtonsGatedOnSelection", context -> render(context, NOBODY_FIXTURES, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    final Query revoke = play.getByRole("button", TextMatch.containing(REVOKE_TITLE));
                    final Query openUser = play.getByRole("button", OPEN_USER_TITLE);
                    // The first user is selected when the list loads, so both buttons start live; a
                    // filter that finds nobody leaves none selected, and they go dead
                    play.expect(revoke).not().toHaveAttribute("aria-disabled");
                    play.type(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "nobody");
                    play.waitFor(3000, () -> play.expect(play.queryByText("Alice Smith")).toBeNull());
                    play.waitFor(() -> play.expect(usersGrid(play).querySelectorAll("." + StroomDom.SELECTED_ROW))
                            .toHaveLength(0));
                    play.expect(revoke).toHaveAttribute("aria-disabled", "true");
                    play.expect(openUser).toHaveAttribute("aria-disabled", "true");
                    SecurityPlays.expectNoProblems(play);
                })
                // A row with no stroom user can't be opened, but can be revoked
                .story("OpenUserNeedsAStroomUser", context -> render(context, false))
                .withPlay(play -> {
                    play.click(play.findAllByText("orphan.subject").nth(0));
                    play.expect(play.getByRole("button", OPEN_USER_TITLE)).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", TextMatch.containing(REVOKE_TITLE)))
                            .not().toHaveAttribute("aria-disabled");
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
                    play.click(play.getByRole("button", "End Selected Session"));
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
                // Revoking a user's access selects the user now in their place, whose sessions are
                // shown (UI-GUIDE.md, "What a list selects"); it once selected the first
                .story("RevokeSelectsNext", context -> render(context, REVOKED_FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Alice Smith"));
                    play.click(play.getByRole("button", TextMatch.containing(REVOKE_TITLE)));
                    screen.findByText(TextMatch.containing(
                            "End all sessions and revoke all tokens for 'Alice Smith'?"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    final Play users = usersGrid(play);
                    play.waitFor(() -> play.expect(users.queryByText("Alice Smith")).toBeNull());
                    play.waitFor(() -> play.expect(users.getAllByText("orphan.subject").nth(0)
                            .closest(StroomDom.GRID_ROW)).toHaveAttribute("aria-selected", "true"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userAccess/v1/sessions").withQuery("subjectId=orphan.subject")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Ending a session selects the session now in its place; the list once lost its
                // selection on every fetch
                .story("EndSessionSelectsNext", context -> render(context, ENDED_FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Alice Smith"));
                    play.click(play.findByText("curl/8.5.0"));
                    play.click(play.getByRole("button", "End Selected Session"));
                    screen.findByText(TextMatch.containing("end this session for 'Alice Smith' on node 'node2'?"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    final Play sessions = sessionsGrid(play);
                    play.waitFor(() -> play.expect(sessions.queryByText("curl/8.5.0")).toBeNull());
                    play.waitFor(() -> play.expect(sessions.getByText("Mozilla/5.0 (X11; Linux x86_64)")
                            .closest(StroomDom.GRID_ROW)).toHaveAttribute("aria-selected", "true"));
                    play.expect(play.getByRole("button", "End Selected Session"))
                            .not().toHaveAttribute("aria-disabled");
                    SecurityPlays.expectNoProblems(play);
                })
                // The quick filter goes to the server
                .story("FilterIsServerSide", context -> render(context, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    // The quick filter has no label, only a placeholder
                    play.type(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "ali");
                    play.waitFor(3000, () -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userAccess/v1/find")
                                    .withJsonBodyContaining("{\"filter\": \"ali\"}")
                                    .toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // The pager's Refresh keeps the user chosen selected, with their sessions shown (once,
                // every fetch selected the first user, so their sessions were shown instead)
                .story("RefreshKeepsSelection", context -> render(context, REFRESHED_FIXTURES, false))
                .withPlay(play -> {
                    play.findByText("Alice Smith");
                    final Play users = usersGrid(play);
                    play.click(users.getAllByText("orphan.subject").nth(0));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/userAccess/v1/sessions").withQuery("subjectId=orphan.subject")
                                    .toSpyMatcher()));
                    play.click(users.getByRole("button", "Refresh"));
                    // The refreshed users have arrived
                    play.waitFor(() -> play.expect(users.getByText("node1, node3")).toBeInTheDocument());
                    play.expect(users.getByText("node1, node3").closest(StroomDom.GRID_ROW))
                            .toHaveAttribute("aria-selected", "true");
                    play.expect(users.getByText("Alice Smith").closest(StroomDom.GRID_ROW))
                            .toHaveAttribute("aria-selected", "false");
                    // Alice's sessions were fetched only once, when the list first loaded
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.expect("requests for Alice's sessions", () -> SecurityPlays.requests(requests,
                            RequestMatcher.post("/userAccess/v1/sessions").withQuery("subjectId=alice")).size())
                            .toBe(1);
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// GWT's grids have no role="grid", so each is found as the pager view holding its toolbar button.
    ///
    /// @return The users grid (with its toolbar and pager).
    private static Play usersGrid(final Play play) {
        return play.within(play.getByTitle(OPEN_USER_TITLE).closest(".dock-container-vertical"));
    }

    /// @return The selected user's sessions grid (with its toolbar and pager).
    private static Play sessionsGrid(final Play play) {
        return play.within(play.getByTitle("End Selected Session").closest(".dock-container-vertical"));
    }

    private static Widget render(final StoryContext context, final boolean externalIdp) {
        return render(context, FIXTURES, externalIdp);
    }

    /// @param fixtures    The story's fixtures.
    /// @param externalIdp Whether Stroom uses an external identity provider.
    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final boolean externalIdp) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
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
            final UserAccessController controller = harness.show(injector.getUserAccessController());
            controller.refresh();
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
