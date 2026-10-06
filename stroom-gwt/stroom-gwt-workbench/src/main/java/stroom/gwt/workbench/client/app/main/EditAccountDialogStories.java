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
import stroom.security.identity.client.presenter.EditAccountPresenter;
import stroom.security.identity.shared.Account;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/EditAccountDialog` in the React Storybook, showing Stroom's real
/// [EditAccountPresenter] (the 'Edit Account' and 'Create Account' dialogs of the 'Manage Accounts'
/// tab) with fake REST replies.
///
/// The React story's `AccountApi` becomes routes for Stroom's `AccountResource`: `update` →
/// `PUT /account/v1/{id}` (its recorder of the changes sent becomes checks on the request spy),
/// `fetch` (the re-read after Unlock/Reactivate) → `GET /account/v1/{id}` and `create` →
/// `POST /account/v1/`. The account is given to the dialog as `AccountsListPresenter` gives it (the
/// selected row), and React's `onChanged` is a spy on the dialog's change handler (which Stroom
/// gives the grid's `refresh`). The presenter comes from GIN.
public final class EditAccountDialogStories {

    /// The name of the spy recording the dialog's change handler (the host grid's refresh).
    static final String ON_CHANGED = "onChanged";

    private static final String ACCOUNT_PATH = "/account/v1/1";
    private static final long SIGNED_IN_MS = 1_700_000_000_000L;
    private static final long LOCKED_UNTIL_MS = 4_100_000_000_000L;

    private static final AccountSpec ACCOUNT = new AccountSpec();
    private static final AccountSpec LOCKED_UNTIL = new AccountSpec()
            .failures(3)
            .locked(SIGNED_IN_MS, LOCKED_UNTIL_MS);
    private static final AccountSpec LOCKED_INDEFINITELY = new AccountSpec()
            .failures(5)
            .locked(SIGNED_IN_MS, null);
    private static final AccountSpec WITH_FAILURES = new AccountSpec().failures(2);
    private static final AccountSpec INACTIVE = new AccountSpec().inactive(true).lastLogin(SIGNED_IN_MS);
    private static final AccountSpec REACTIVATED = new AccountSpec().inactive(false).lastLogin(SIGNED_IN_MS);
    private static final AccountSpec NEVER_SIGNED_IN = new AccountSpec().inactive(true);

    private EditAccountDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/EditAccountDialog", EditAccountDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Only the changed values are sent (EditAccountPresenter.updateAccount)
                .story("SendsOnlyChangedValues", context -> render(context, ACCOUNT, ACCOUNT))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    final Query first = screen.getByLabelText("First Name");
                    // Differs from React: RestyGWT sends the members left alone as null (and no
                    // actions as []), where React leaves them out
                    play.clear(first);
                    play.type(first, "Administrator");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    // Everything left alone is absent, not echoed back
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            SecurityPlays.withOnlyValues(RequestMatcher.put(ACCOUNT_PATH),
                                    "{\"firstName\": \"Administrator\"}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Enabled travels as an action (actionIfChanged(..., ENABLE, DISABLE))
                .story("EnabledTravelsAsAnAction", context -> render(context, ACCOUNT, ACCOUNT))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.click(enabledTickBox(screen));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            SecurityPlays.withOnlyValues(RequestMatcher.put(ACCOUNT_PATH),
                                    "{\"actions\": [\"DISABLE\"]}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Saving with nothing edited sends an empty change
                .story("NoEditsSendsEmptyChange", context -> render(context, ACCOUNT, ACCOUNT))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            SecurityPlays.withOnlyValues(RequestMatcher.put(ACCOUNT_PATH), "{}").toSpyMatcher()));
                    expectNoProblems(play);
                })
                // No Inactive or Locked tick boxes: Enabled remains
                .story("NoInactiveOrLockedTickBoxes", context -> render(context, ACCOUNT, ACCOUNT))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.expect(enabledTickBox(screen)).toBeInTheDocument();
                    play.expect(screen.queryByLabelText("Inactive")).toBeNull();
                    play.expect(screen.queryByLabelText("Locked")).toBeNull();
                    expectNoProblems(play);
                })
                // Create mode: the same fields without the Enabled toggle
                .story("CreateMode", context -> render(context, null, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Create Account");
                    // Differs from React: GWT hides the Enabled form group (setEnabledVisible(false))
                    // rather than leaving it out, so its tick box is still in the document
                    play.expect(enabledTickBox(screen)).not().toBeVisible();
                    expectNoProblems(play);
                })
                // A user id under three characters is rejected
                .story("ShortUserIdRejected", context -> render(context, null, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Create Account");
                    play.type(screen.getByLabelText("User Id"), "ab");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText("A user id must be at least 3 characters."))
                            .toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("ERROR: A user id must be at least 3 characters.");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/account/v1/").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A lock with an end time names it; Unlock is live
                .story("LockedUntilState", context -> render(context, LOCKED_UNTIL, LOCKED_UNTIL))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.expect(screen.getByText(TextMatch.regex("^Locked until .*, after 3 failed sign-ins$")))
                            .toBeInTheDocument();
                    play.expect(screen.getByRole("button", StroomDom.button("Unlock"))).not().toBeDisabled();
                    expectNoProblems(play);
                })
                // A lock with no end time says it won't clear on its own
                .story("LockedIndefinitelyState",
                        context -> render(context, LOCKED_INDEFINITELY, LOCKED_INDEFINITELY))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.expect(screen.getByText("Locked after 5 failed sign-ins - will not clear on its own"))
                            .toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Not locked: nothing to undo, so Unlock is dead
                .story("NotLockedStates", context -> render(context, ACCOUNT, ACCOUNT))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.expect(screen.getByText("Not locked")).toBeInTheDocument();
                    play.expect(screen.getByRole("button", StroomDom.button("Unlock"))).toBeDisabled();
                    expectNoProblems(play);
                })
                // Not locked, but the failures are reported
                .story("NotLockedWithFailuresState", context -> render(context, WITH_FAILURES, WITH_FAILURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.expect(screen.getByText("Not locked - 2 failed sign-ins since the last successful one"))
                            .toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Unlock applies at once, then re-reads the account
                .story("UnlockAppliesImmediately", context -> render(context, LOCKED_UNTIL, ACCOUNT))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.click(screen.getByRole("button", StroomDom.button("Unlock")));
                    // A lone UNLOCK action, with no field values
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            SecurityPlays.withOnlyValues(RequestMatcher.put(ACCOUNT_PATH),
                                    "{\"actions\": [\"UNLOCK\"]}")
                                    .toSpyMatcher()));
                    // The re-read result is shown, and the button has gone dead
                    play.waitFor(() -> play.expect(screen.getByText("Not locked")).toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get(ACCOUNT_PATH).toSpyMatcher());
                    play.expect(screen.getByRole("button", StroomDom.button("Unlock"))).toBeDisabled();
                    // The dialog stays open, and the host is told to refresh
                    play.expect(screen.getByText("Edit Account")).toBeInTheDocument();
                    play.expect(play.spy(ON_CHANGED)).toHaveBeenCalledTimes(1);
                    expectNoProblems(play);
                })
                // The activity line, and Reactivate
                .story("InactiveState", context -> render(context, INACTIVE, REACTIVATED))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.expect(screen.getByText(TextMatch.startingWith("Inactive - last signed in ")))
                            .toBeInTheDocument();
                    play.click(screen.getByRole("button", StroomDom.button("Reactivate")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            SecurityPlays.withOnlyValues(RequestMatcher.put(ACCOUNT_PATH),
                                    "{\"actions\": [\"REACTIVATE\"]}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.getByText("Active")).toBeInTheDocument());
                    play.expect(screen.getByRole("button", StroomDom.button("Reactivate"))).toBeDisabled();
                    expectNoProblems(play);
                })
                // Never signed in
                .story("InactiveNeverSignedInState", context -> render(context, NEVER_SIGNED_IN, NEVER_SIGNED_IN))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Edit Account");
                    play.expect(screen.getByText("Inactive - never signed in")).toBeInTheDocument();
                    expectNoProblems(play);
                });
    }

    /// Differs from React: the 'Enabled' label is for the tick box's container (a `div`), not its
    /// input, so the tick box has no accessible name; it is found in the label's form group (by
    /// type, not role, as a hidden one has no role).
    ///
    /// @return The 'Enabled' tick box.
    private static Query enabledTickBox(final Play screen) {
        return screen.within(screen.getByText("Enabled", "label").closest(".form-group"))
                .querySelector("input[type='checkbox']");
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    /// @param account     The account being edited, or null to create one.
    /// @param afterAction The account the server returns when the dialog re-reads it.
    private static Widget render(final StoryContext context,
                                 final AccountSpec account,
                                 final AccountSpec afterAction) {
        final RestFixtures.Builder fixtures = RestFixtures.builder()
                .put(ACCOUNT_PATH, RestReply.json("true"))
                .post("/account/v1/", RestReply.json("2"));
        if (afterAction != null) {
            fixtures.get(ACCOUNT_PATH, RestReply.json(afterAction.toJson()));
        }
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures.build())
                .injector(injector)
                .realAlerts()
                .build();
        harness.fn(ON_CHANGED);
        final Runnable onChanged = () -> harness.spy(ON_CHANGED, "");

        // Shown once the user's preferences are loaded, as the lock and activity lines format
        // their times with them
        harness.afterStartUp(() -> {
            final EditAccountPresenter presenter = injector.getEditAccountPresenter();
            if (account == null) {
                presenter.showCreateDialog(onChanged);
            } else {
                presenter.showEditDialog(account.toAccount(), onChanged);
            }
        });
        return harness.asWidget();
    }


    // --------------------------------------------------------------------------------


    /// React's `Account` fixture (the `admin` account) and its variations: the account given to
    /// the dialog and, as JSON, the one the server returns.
    private static final class AccountSpec {

        private int failureCount;
        private Long failureLockedMs;
        private Long failureLockedUntilMs;
        private boolean inactive;
        private Long lastLoginMs;

        AccountSpec failures(final int failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        AccountSpec locked(final Long failureLockedMs, final Long failureLockedUntilMs) {
            this.failureLockedMs = failureLockedMs;
            this.failureLockedUntilMs = failureLockedUntilMs;
            return this;
        }

        AccountSpec inactive(final boolean inactive) {
            this.inactive = inactive;
            return this;
        }

        AccountSpec lastLogin(final Long lastLoginMs) {
            this.lastLoginMs = lastLoginMs;
            return this;
        }

        Account toAccount() {
            final Account account = new Account();
            account.setId(1);
            account.setUserId("admin");
            account.setFirstName("Admin");
            account.setLastName("User");
            account.setEmail("admin@example.com");
            account.setComments("Initial account");
            account.setEnabled(true);
            account.setInactive(inactive);
            account.setNeverExpires(false);
            account.setFailureCount(failureCount);
            account.setFailureLockedMs(failureLockedMs);
            account.setFailureLockedUntilMs(failureLockedUntilMs);
            account.setLastLoginMs(lastLoginMs);
            return account;
        }

        String toJson() {
            return """
                    {"id": 1, "userId": "admin", "firstName": "Admin", "lastName": "User",
                      "email": "admin@example.com", "comments": "Initial account", "enabled": true,
                      "inactive": INACTIVE, "neverExpires": false, "failureCount": FAILURES,
                      "failureLockedMs": LOCKED, "failureLockedUntilMs": UNTIL, "lastLoginMs": LAST}"""
                    .replace("INACTIVE", String.valueOf(inactive))
                    .replace("FAILURES", String.valueOf(failureCount))
                    .replace("LOCKED", String.valueOf(failureLockedMs))
                    .replace("UNTIL", String.valueOf(failureLockedUntilMs))
                    .replace("LAST", String.valueOf(lastLoginMs));
        }
    }
}
