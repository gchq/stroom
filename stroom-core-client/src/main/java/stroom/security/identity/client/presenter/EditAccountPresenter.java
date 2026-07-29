/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.identity.client.presenter.EditAccountPresenter.EditAccountView;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountAction;
import stroom.security.identity.shared.AccountChange;
import stroom.security.identity.shared.AccountResource;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.security.shared.UserResource;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class EditAccountPresenter
        extends MyPresenterWidget<EditAccountView>
        implements EditAccountUiHandlers, HasHandlers {

    private static final AccountResource ACCOUNT_RESOURCE = GWT.create(AccountResource.class);
    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;

    private Account account;
    private Runnable onChangeHandler;
    private final Provider<ChangePasswordPresenter> changePasswordPresenterProvider;
    private String password;
    private String confirmPassword;

    @Inject
    public EditAccountPresenter(final EventBus eventBus,
                                final EditAccountView view,
                                final RestFactory restFactory,
                                final DateTimeFormatter dateTimeFormatter,
                                final Provider<ChangePasswordPresenter> changePasswordPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.changePasswordPresenterProvider = changePasswordPresenterProvider;
        getView().setUiHandlers(this);
    }

    public void showCreateDialog(final Runnable onChangeHandler) {
        this.onChangeHandler = onChangeHandler;
        this.account = null;

        getView().setEnabledVisible(false);
        getView().setInactiveVisible(false);
        getView().setLockedVisible(false);
        getView().setPasswordButtonText(getPasswordCaption());

        final PopupSize popupSize = PopupSize.resizableX(400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .icon(SvgImage.USER)
                .caption("Create Account")
                .modal(true)
                .onShow(e -> getView().focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    public void showEditDialog(final Account account,
                               final Runnable onChangeHandler) {
        this.onChangeHandler = onChangeHandler;
        this.account = account;

        getView().setUserId(account.getUserId());
        getView().setEmail(account.getEmail());
        getView().setFirstName(account.getFirstName());
        getView().setLastName(account.getLastName());
        getView().setComments(account.getComments());
        getView().setNeverExpires(account.isNeverExpires());
        // Set explicitly rather than relying on the template default, so that this does not depend on the
        // presenter being a fresh instance - showCreateDialog hides all three.
        getView().setEnabledVisible(true);
        getView().setLockedVisible(true);
        getView().setInactiveVisible(true);
        getView().setEnabled(account.isEnabled());
        showLockAndActivity();
        getView().setPasswordButtonText(getPasswordCaption());

        final PopupSize popupSize = PopupSize.resizableX(400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .icon(SvgImage.USER)
                .caption("Edit Account")
                .modal(true)
                .onShow(e -> getView().focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    private String getPasswordCaption() {
        return account == null
                ? "Set Password"
                : "Change Password";
    }

    @Override
    public void onChangePassword() {
        final ChangePasswordPresenter changePasswordPresenter = changePasswordPresenterProvider.get();
        changePasswordPresenter.show(getPasswordCaption(), e -> {
            if (e.isOk()) {
                if (changePasswordPresenter.validate()) {
                    this.password = changePasswordPresenter.getPassword();
                    this.confirmPassword = changePasswordPresenter.getConfirmPassword();
                    e.hide();
                } else {
                    e.reset();
                }
            } else {
                e.hide();
            }
        });
    }

    private void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            if (NullSafe.isBlankString(getView().getUserId())) {
                AlertEvent.fireError(this, "A user id must be provided for the account.", e::reset);
            } else if (getView().getUserId().length() < 3) {
                AlertEvent.fireError(this, "A user id must be at least 3 characters.", e::reset);
            } else if (!NullSafe.isBlankString(getView().getEmail()) &&
                       !EmailValidator.validate(getView().getEmail())) {
                AlertEvent.fireError(this, "Invalid email address.", e::reset);
            } else {
                if (account == null) {
                    createAccount(e);

                } else {
                    updateAccount(e);
                }
            }
        } else {
            e.hide();
        }
    }

    private void createAccount(final HidePopupRequestEvent e) {
        final CreateAccountRequest request = new CreateAccountRequest(
                getView().getFirstName(),
                getView().getLastName(),
                getView().getUserId(),
                getView().getEmail(),
                getView().getComments(),
                password,
                confirmPassword,
                true,
                getView().isNeverExpires());
        restFactory
                .create(ACCOUNT_RESOURCE)
                .method(res -> res.create(request))
                .onSuccess(id -> {
                    onChangeHandler.run();
                    e.hide();
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(this, "Error creating account: "
                                                   + throwable.getMessage(), e::reset))
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    public void onUnlock() {
        applyImmediately(AccountAction.UNLOCK, "Error unlocking account: ");
    }

    @Override
    public void onReactivate() {
        applyImmediately(AccountAction.REACTIVATE, "Error reactivating account: ");
    }

    /**
     * Applies a single action now rather than staging it into the save. Each is one unambiguous act with
     * nothing to combine it with, and doing it this way keeps them working while the administrator has
     * unsaved edits to the text fields.
     */
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
                            onChangeHandler.run();
                        })
                        .taskMonitorFactory(this)
                        .exec())
                .onFailure(throwable ->
                        AlertEvent.fireError(this, errorPrefix + throwable.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();
    }

    private void showLockAndActivity() {
        getView().setLockState(describeLock(), account.isLocked());
        getView().setActivityState(describeActivity(), account.isInactive());
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
        // Only what was actually altered is sent. The account this screen was opened with is a snapshot, and
        // returning it whole would write back every column - including ones the login path owns, undoing a
        // lockout or a login that happened while the screen was open.
        final AccountChange.Builder builder = AccountChange.builder()
                .valueIfChanged(getView().getUserId(), account.getUserId(), AccountChange.Builder::userId)
                .valueIfChanged(getView().getEmail(), account.getEmail(), AccountChange.Builder::email)
                .valueIfChanged(getView().getFirstName(), account.getFirstName(),
                        AccountChange.Builder::firstName)
                .valueIfChanged(getView().getLastName(), account.getLastName(), AccountChange.Builder::lastName)
                .valueIfChanged(getView().getComments(), account.getComments(), AccountChange.Builder::comments)
                // Enabled is the only one of the three account states an administrator sets, so it is the
                // only one carried by the save. Unlocking and reactivating are their own actions, applied
                // when their button is pressed.
                .actionIfChanged(getView().isEnabled(), account.isEnabled(),
                        AccountAction.ENABLE, AccountAction.DISABLE);

        if (getView().isNeverExpires() != account.isNeverExpires()) {
            builder.neverExpires(getView().isNeverExpires());
        }
        if (password != null) {
            builder.password(password, confirmPassword);
        }

        final AccountChange change = builder.build();
        restFactory
                .create(ACCOUNT_RESOURCE)
                .method(res -> res.update(change, account.getId()))
                .onSuccess(result -> {
                    onChangeHandler.run();
                    e.hide();
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(this, "Error updating account: "
                                                   + throwable.getMessage(), e::reset))
                .taskMonitorFactory(this)
                .exec();
    }


    // --------------------------------------------------------------------------------


    public interface EditAccountView extends View, Focus, HasUiHandlers<EditAccountUiHandlers> {

        void setUserId(String userId);

        String getUserId();

        void setUserIdFeedback(String feedback);

        void setEmail(String email);

        String getEmail();

        void setEmailFeedback(String feedback);

        void setFirstName(String firstName);

        String getFirstName();

        void setLastName(String lastName);

        String getLastName();

        void setComments(String comments);

        String getComments();

        void setNeverExpires(boolean neverExpires);

        boolean isNeverExpires();

        void setNeverExpiresVisible(boolean visible);

        void setEnabled(boolean enabled);

        boolean isEnabled();

        void setEnabledVisible(boolean visible);

        /**
         * Shows what the lockout has done and whether there is anything to undo. Read only: an
         * administrator does not lock an account, repeated wrong passwords do.
         */
        void setLockState(String description, boolean canUnlock);

        void setLockedVisible(boolean visible);

        /**
         * Shows whether the account has gone unused. Read only, as for {@link #setLockState}: inactivity is
         * applied by the account maintenance job, not by an administrator.
         */
        void setActivityState(String description, boolean canReactivate);

        void setInactiveVisible(boolean visible);

        void setPasswordButtonText(String text);
    }
}
