/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.identity.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountAction;
import stroom.security.identity.shared.AccountChange;
import stroom.security.identity.shared.AccountResource;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// Creates or edits an internal identity provider account ([EditAccount]) in a dialog made for one
/// use. Unlocking and reactivating are applied at once; everything else when OK is pressed.
public class EditAccountController extends Controller {

    private static final AccountResource ACCOUNT_RESOURCE = GWT.create(AccountResource.class);

    private final EditAccount view;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final Provider<ChangePasswordController> changePasswordProvider;

    private Account account;
    private Runnable onChange;
    private String password;
    private String confirmPassword;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The form.
    /// @param restFactory            Creates, updates, unlocks and reactivates the account.
    /// @param dateTimeFormatter      Formats the lock and activity times in the user's preferences.
    /// @param changePasswordProvider Makes the dialog that asks for the account's password.
    @Inject
    public EditAccountController(final EventBus eventBus,
                                 final EditAccount view,
                                 final RestFactory restFactory,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final Provider<ChangePasswordController> changePasswordProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.changePasswordProvider = changePasswordProvider;
        initWidget(view);
        view.setOnChangePassword(this::onChangePassword);
        view.setOnUnlock(() -> applyImmediately(AccountAction.UNLOCK, "Error unlocking account: "));
        view.setOnReactivate(() -> applyImmediately(AccountAction.REACTIVATE, "Error reactivating account: "));
    }

    /// Asks for a new account in a dialog, owned by `opener` until it is closed, and creates it on
    /// OK.
    ///
    /// @param opener   What shows it.
    /// @param onChange Run once the account is created.
    public void create(final HasControllerNode opener, final Runnable onChange) {
        this.onChange = onChange;
        this.account = null;
        view.setStateVisible(false);
        show(opener, "Create Account");
    }

    /// Shows an account in a dialog, owned by `opener` until it is closed, and saves what is
    /// changed on OK.
    ///
    /// @param opener   What shows it.
    /// @param account  The account.
    /// @param onChange Run whenever the account is changed.
    public void edit(final HasControllerNode opener, final Account account, final Runnable onChange) {
        this.onChange = onChange;
        this.account = account;
        view.setUserId(account.getUserId());
        view.setEmail(account.getEmail());
        view.setFirstName(account.getFirstName());
        view.setLastName(account.getLastName());
        view.setComments(account.getComments());
        view.setNeverExpires(account.isNeverExpires());
        view.setStateVisible(true);
        view.setEnabled(account.isEnabled());
        showLockAndActivity();
        show(opener, "Edit Account");
    }

    private void show(final HasControllerNode opener, final String caption) {
        view.setPasswordButtonText(getPasswordCaption());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX(400))
                .icon(SvgImage.USER)
                .caption(caption)
                .modal(true)
                .onShow(e -> view.focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    private String getPasswordCaption() {
        return account == null
                ? "Set Password"
                : "Change Password";
    }

    private void onChangePassword() {
        changePasswordProvider.get().show(this, getPasswordCaption(), (password, confirmPassword, e) -> {
            this.password = password;
            this.confirmPassword = confirmPassword;
            e.hide();
        }, () -> {
        });
    }

    private void onHideRequest(final HidePopupRequestEvent e) {
        if (!e.isOk()) {
            e.hide();
        } else if (NullSafe.isBlankString(view.getUserId())) {
            AlertEvent.fireError(this, "A user id must be provided for the account.", e::reset);
        } else if (view.getUserId().length() < 3) {
            AlertEvent.fireError(this, "A user id must be at least 3 characters.", e::reset);
        } else if (!NullSafe.isBlankString(view.getEmail()) && !EmailValidator.validate(view.getEmail())) {
            AlertEvent.fireError(this, "Invalid email address.", e::reset);
        } else if (account == null) {
            createAccount(e);
        } else {
            updateAccount(e);
        }
    }

    private void createAccount(final HidePopupRequestEvent e) {
        final CreateAccountRequest request = new CreateAccountRequest(
                view.getFirstName(),
                view.getLastName(),
                view.getUserId(),
                view.getEmail(),
                view.getComments(),
                password,
                confirmPassword,
                true,
                view.isNeverExpires());
        restFactory
                .create(ACCOUNT_RESOURCE)
                .method(res -> res.create(request))
                .onSuccess(id -> {
                    onChange.run();
                    e.hide();
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(this, "Error creating account: "
                                                   + throwable.getMessage(), e::reset))
                .owner(this)
                .exec();
    }

    // Applies a single action now rather than staging it into the save. Each is one unambiguous act
    // with nothing to combine it with, and doing it this way keeps them working while the
    // administrator has unsaved edits to the text fields.
    private void applyImmediately(final AccountAction action, final String errorPrefix) {
        if (account == null) {
            // Both buttons live in groups hidden while creating an account, so this cannot normally happen.
            return;
        }
        final AccountChange change = AccountChange.builder().action(action).build();
        restFactory
                .create(ACCOUNT_RESOURCE)
                .method(res -> res.update(change, account.getId()))
                .onSuccess(result -> restFactory
                        .create(ACCOUNT_RESOURCE)
                        .method(res -> res.fetch(account.getId()))
                        .onSuccess(updated -> {
                            // Re-read rather than assume, so the dialog shows what was actually stored.
                            account = updated;
                            showLockAndActivity();
                            onChange.run();
                        })
                        .owner(this)
                        .exec())
                .onFailure(throwable ->
                        AlertEvent.fireError(this, errorPrefix + throwable.getMessage(), null))
                .owner(this)
                .exec();
    }

    private void showLockAndActivity() {
        view.setLockState(describeLock(), account.isLocked());
        view.setActivityState(describeActivity(), account.isInactive());
    }

    private String describeLock() {
        final int failures = account.getFailureCount();
        if (!account.isLocked()) {
            return failures == 0
                    ? "Not locked"
                    : "Not locked - " + failures + " failed sign-ins since the last successful one";
        }
        final Long until = account.getFailureLockedUntilMs();
        return until == null
                ? "Locked after " + failures + " failed sign-ins - will not clear on its own"
                : "Locked until " + dateTimeFormatter.format(until) + ", after " + failures
                  + " failed sign-ins";
    }

    private String describeActivity() {
        if (!account.isInactive()) {
            return "Active";
        }
        final Long lastLogin = account.getLastLoginMs();
        return lastLogin == null
                ? "Inactive - never signed in"
                : "Inactive - last signed in " + dateTimeFormatter.format(lastLogin);
    }

    private void updateAccount(final HidePopupRequestEvent e) {
        // Only what was actually altered is sent. The account this screen was opened with is a
        // snapshot, and returning it whole would write back every column - including ones the login
        // path owns, undoing a lockout or a login that happened while the screen was open.
        final AccountChange.Builder builder = AccountChange.builder()
                .valueIfChanged(view.getUserId(), account.getUserId(), AccountChange.Builder::userId)
                .valueIfChanged(view.getEmail(), account.getEmail(), AccountChange.Builder::email)
                .valueIfChanged(view.getFirstName(), account.getFirstName(), AccountChange.Builder::firstName)
                .valueIfChanged(view.getLastName(), account.getLastName(), AccountChange.Builder::lastName)
                .valueIfChanged(view.getComments(), account.getComments(), AccountChange.Builder::comments)
                // Enabled is the only one of the three account states an administrator sets, so it
                // is the only one carried by the save. Unlocking and reactivating are their own
                // actions, applied when their button is pressed.
                .actionIfChanged(view.isEnabled(), account.isEnabled(),
                        AccountAction.ENABLE, AccountAction.DISABLE);

        if (view.isNeverExpires() != account.isNeverExpires()) {
            builder.neverExpires(view.isNeverExpires());
        }
        if (password != null) {
            builder.password(password, confirmPassword);
        }

        final AccountChange change = builder.build();
        restFactory
                .create(ACCOUNT_RESOURCE)
                .method(res -> res.update(change, account.getId()))
                .onSuccess(result -> {
                    onChange.run();
                    e.hide();
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(this, "Error updating account: "
                                                   + throwable.getMessage(), e::reset))
                .owner(this)
                .exec();
    }
}
