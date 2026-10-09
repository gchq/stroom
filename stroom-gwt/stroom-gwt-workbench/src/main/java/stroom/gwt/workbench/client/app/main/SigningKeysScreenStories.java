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
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.presenter.SigningKeyPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/SigningKeysScreen`, showing Stroom's real [SigningKeyPresenter] (the
/// 'Signing Keys' tab) with fake REST replies.
///
/// The fixtures answer Stroom's `SigningKeyResource`: the keys (`GET /signingKey/v1/list`),
/// revoking one (`POST /signingKey/v1/revoke?id=N`) and revoking all
/// (`POST /signingKey/v1/revokeAll`); the calls are checked on the request spy. The user is an
/// administrator, as `SigningKeyPlugin` requires. Confirmations are Stroom's real dialogs. The
/// presenter comes from GIN and is opened as `SigningKeyPlugin` opens it (refreshed).
public final class SigningKeysScreenStories {

    private static final String REVOKE_TITLE = "Revoke the selected signing key";
    private static final String REVOKE_ALL_TITLE = "Revoke every signing key still in use";

    // The active key has no end date at all
    private static final String ACTIVE = "{\"id\": 1, \"status\": \"ACTIVE\", \"issuedMs\": 1700000000000}";
    private static final String RETIRED = """
            {"id": 2, "status": "RETIRED", "issuedMs": 1690000000000, "expiresMs": 1800000000000}""";
    private static final String EXPIRED = """
            {"id": 3, "status": "EXPIRED", "issuedMs": 1680000000000, "expiresMs": 1690000000000}""";
    private static final String REVOKED = "{\"id\": 4, \"status\": \"REVOKED\", \"issuedMs\": 1670000000000}";

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
                    play.expect(revoke).toHaveClass("disabled");
                    play.click(play.getByText("Revoked"));
                    play.expect(revoke).toHaveClass("disabled");
                    play.click(play.getByText("Active"));
                    play.expect(revoke).not().toHaveClass("disabled");
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
                    play.expect(revokeAll).not().toHaveClass("disabled");
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
                });
    }

    private static Widget render(final StoryContext context, final String... rows) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get("/signingKey/v1/list", RestReply.json("[" + String.join(", ", rows) + "]"))
                .post("/signingKey/v1/revoke", RestReply.json("1"))
                .post("/signingKey/v1/revokeAll", RestReply.json(String.valueOf(rows.length)))
                .build();
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        // Opened as SigningKeyPlugin opens it (refreshed), once Stroom has started (the dates are
        // formatted with the user's preferences)
        harness.afterStartUp(() -> {
            final SigningKeyPresenter presenter = harness.addContent(injector.getSigningKeyPresenter());
            presenter.refresh();
        });
        return harness.asWidget();
    }
}
