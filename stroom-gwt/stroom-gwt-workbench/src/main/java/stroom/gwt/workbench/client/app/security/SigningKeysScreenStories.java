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
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.component.SigningKeysController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/SigningKeysScreen`, showing Stroom's real [SigningKeysController] (the
/// 'Signing Keys' tab) with fake REST replies.
///
/// The fixtures answer Stroom's `SigningKeyResource`: the keys (`GET /signingKey/v1/list`),
/// revoking one (`POST /signingKey/v1/revoke?id=N`) and revoking all
/// (`POST /signingKey/v1/revokeAll`); the calls are checked on the request spy. The user is an
/// administrator, as `SigningKeyPlugin` requires. Confirmations are Stroom's real dialogs. The
/// controller comes from GIN and is opened as `SigningKeyPlugin` opens it (refreshed).
public final class SigningKeysScreenStories {

    private static final String REVOKE_TITLE = "Revoke Selected Signing Key";
    private static final String REVOKE_ALL_TITLE = "Revoke All Signing Keys Still in Use";

    // The active key has no end date at all
    private static final String ACTIVE = "{\"id\": 1, \"status\": \"ACTIVE\", \"issuedMs\": 1700000000000}";
    private static final String RETIRED = """
            {"id": 2, "status": "RETIRED", "issuedMs": 1690000000000, "expiresMs": 1800000000000}""";
    private static final String EXPIRED = """
            {"id": 3, "status": "EXPIRED", "issuedMs": 1680000000000, "expiresMs": 1690000000000}""";
    private static final String REVOKED = "{\"id\": 4, \"status\": \"REVOKED\", \"issuedMs\": 1670000000000}";
    // The retired key once it has been revoked (elsewhere)
    private static final String RETIRED_NOW_REVOKED =
            "{\"id\": 2, \"status\": \"REVOKED\", \"issuedMs\": 1690000000000}";

    // The screen the current rendering shows, so a play can refresh it as Stroom's content pane
    // does (HasRefresh.refresh())
    private static SigningKeysController shownController;

    private SigningKeysScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/SigningKeysScreen", SigningKeysScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Two columns; the trust window's end rides with the status
                .story("StatusCarriesTheTrustWindow",
                        context -> render(context, ACTIVE, RETIRED, EXPIRED, REVOKED))
                .withPlay(play -> {
                    play.findByText("Active");
                    play.expect(play.getAllByRole("columnheader")).toHaveLength(2);
                    play.expect(play.getByRole("columnheader", "Status")).toBeInTheDocument();
                    play.expect(play.getByRole("columnheader", "Issued")).toBeInTheDocument();
                    // Display values, not enum constants; only the dated states carry the suffix
                    play.expect(play.getByText("Active")).toBeInTheDocument();
                    play.expect(play.getByText("Revoked")).toBeInTheDocument();
                    play.expect(play.getByText(TextMatch.regex("^Retired \\(trusted until .+\\)$")))
                            .toBeInTheDocument();
                    play.expect(play.getByText(TextMatch.regex("^Expired \\(trusted until .+\\)$")))
                            .toBeInTheDocument();
                    play.expect(play.queryByText("ACTIVE")).toBeNull();
                    SecurityPlays.expectNoProblems(play);
                })
                // Revoke is dead on an already revoked key
                .story("RevokeDisabledForRevokedKey", context -> render(context, ACTIVE, REVOKED))
                .withPlay(play -> {
                    play.findByText("Revoked");
                    final Query revoke = play.getByRole("button", REVOKE_TITLE);
                    play.expect(revoke).toHaveAttribute("aria-disabled", "true");
                    play.click(play.getByText("Revoked"));
                    play.expect(revoke).toHaveAttribute("aria-disabled", "true");
                    play.click(play.getByText("Active"));
                    play.expect(revoke).not().toHaveAttribute("aria-disabled");
                    SecurityPlays.expectNoProblems(play);
                })
                // Revoking the active key warns about the cluster
                .story("RevokeActiveKeyWarnsAboutTheCluster", context -> render(context, ACTIVE))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Active"));
                    play.click(play.getByRole("button", REVOKE_TITLE));
                    screen.findByText(TextMatch.containing("revoke the signing key currently in use?"));
                    play.expect(screen.getByText(TextMatch.containing(
                            "unable to talk to each other for up to ten minutes"))).toBeInTheDocument();
                    play.expect(screen.getByText(TextMatch.containing(
                            "Revoke it only if you believe the key may have been exposed"))).toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/signingKey/v1/revoke").withQuery("id=1").toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // An expired key is already untrusted
                .story("RevokeExpiredKeySaysNothingChanges", context -> render(context, EXPIRED))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText(TextMatch.startingWith("Expired")));
                    play.click(play.getByRole("button", REVOKE_TITLE));
                    screen.findByText(TextMatch.containing(
                            "It is no longer trusted, so revoking it will not sign anyone out"));
                    SecurityPlays.expectNoProblems(play);
                })
                // Revoke All needs no selection
                .story("RevokeAllNeedsNoSelection", context -> render(context, ACTIVE, RETIRED))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Active");
                    final Query revokeAll = play.getByRole("button", REVOKE_ALL_TITLE);
                    play.expect(revokeAll).not().toHaveAttribute("aria-disabled");
                    play.click(revokeAll);
                    screen.findByText(TextMatch.containing("revoke every signing key?"));
                    play.expect(screen.getByText(TextMatch.containing(
                            "if you believe a key has been exposed but do not know which"))).toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/signingKey/v1/revokeAll").toSpyMatcher()));
                    SecurityPlays.expectNoProblems(play);
                })
                // Cancelling a revoke calls nothing
                .story("CancelRevokesNothing", context -> render(context, ACTIVE))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Active"));
                    play.click(play.getByRole("button", REVOKE_TITLE));
                    screen.findByText(TextMatch.containing("revoke the signing key currently in use?"));
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/signingKey/v1/revoke").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/signingKey/v1/revokeAll").toSpyMatcher());
                    SecurityPlays.expectNoProblems(play);
                })
                // A refresh that finds the selected key revoked (elsewhere) keeps it selected, as it
                // now is, so Revoke goes dead. The selection once kept the key as first fetched (a
                // row is equal only to one with the same values), so the row showed unselected and
                // Revoke stayed live for the old, retired, key
                .story("RefreshFollowsTheSelectedKey", context -> render(context, 2,
                        RestReply.json(list(ACTIVE, RETIRED)), RestReply.json(list(ACTIVE, RETIRED_NOW_REVOKED))))
                .withPlay(play -> {
                    play.click(play.findByText(TextMatch.startingWith("Retired")));
                    final Query revoke = play.getByRole("button", REVOKE_TITLE);
                    play.waitFor(() -> play.expect(revoke).not().toHaveAttribute("aria-disabled"));
                    play.run("the content pane refreshes the tab", () -> shownController.refresh());
                    play.waitFor(() -> play.expect(play.getByText("Revoked")).toBeInTheDocument());
                    play.expect(play.getByText("Revoked").closest(StroomDom.GRID_ROW))
                            .toHaveAttribute("aria-selected", "true");
                    play.expect(play.getByText("Active").closest(StroomDom.GRID_ROW))
                            .toHaveAttribute("aria-selected", "false");
                    play.waitFor(() -> play.expect(revoke).toHaveAttribute("aria-disabled", "true"));
                    SecurityPlays.expectNoProblems(play);
                })
                // A key revoked here is still listed, as revoked, so it stays selected and Revoke goes
                // dead (UI-GUIDE.md, "What a list selects"); a revoke once cleared the selection
                .story("RevokeKeepsIt", context -> render(context, 2,
                        RestReply.json(list(ACTIVE, RETIRED)), RestReply.json(list(ACTIVE, RETIRED_NOW_REVOKED))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText(TextMatch.startingWith("Retired")));
                    final Query revoke = play.getByRole("button", REVOKE_TITLE);
                    play.waitFor(() -> play.expect(revoke).not().toHaveAttribute("aria-disabled"));
                    play.click(revoke);
                    screen.findByText(TextMatch.containing("revoke this signing key?"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.getByText("Revoked")).toBeInTheDocument());
                    play.expect(play.getByText("Revoked").closest(StroomDom.GRID_ROW))
                            .toHaveAttribute("aria-selected", "true");
                    play.waitFor(() -> play.expect(revoke).toHaveAttribute("aria-disabled", "true"));
                    SecurityPlays.expectNoProblems(play);
                });
    }

    // The keys as SigningKeyResource.list() returns them
    private static String list(final String... rows) {
        return "[" + String.join(", ", rows) + "]";
    }

    private static Widget render(final StoryContext context, final String... rows) {
        return render(context, rows.length, RestReply.json(list(rows)));
    }

    /// @param keyCount The number of keys Revoke All revokes.
    /// @param list     The reply to the first fetch of the keys.
    /// @param more     The replies to the fetches after it; the last repeats.
    private static Widget render(final StoryContext context,
                                 final int keyCount,
                                 final RestReply list,
                                 final RestReply... more) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/signingKey/v1/list", list, more)
                .post("/signingKey/v1/revoke", RestReply.json("1"))
                .post("/signingKey/v1/revokeAll", RestReply.json(String.valueOf(keyCount)))
                .build();
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        // Opened as SigningKeyPlugin opens it (refreshed), once Stroom has started (the dates are
        // formatted with the user's preferences)
        harness.afterStartUp(() -> {
            final SigningKeysController controller = harness.show(injector.getSigningKeysController());
            controller.refresh();
            shownController = controller;
            // Not kept once this rendering has gone
            harness.addCleanUp(() -> {
                if (shownController == controller) {
                    shownController = null;
                }
            });
        });
        return harness.asWidget();
    }
}
