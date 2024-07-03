package stroom.security.client.presenter.account;

import stroom.alert.client.event.AlertEvent;
import stroom.changepassword.client.presenter.ChangePasswordPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.account.EditAccountPresenter.EditAccountView;
import stroom.security.shared.account.Account;
import stroom.security.shared.account.AccountResource2;
import stroom.security.shared.account.CreateAccountRequest;
import stroom.security.shared.account.UpdateAccountRequest;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.regexp.shared.RegExp;
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

    private static final String EMAIL_PATTERN = "(" +
            "?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+" +
            "(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
            "|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]" +
            "|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@" +
            "(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+" +
            "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" +
            "|\\[(?:(?:(2(5[0-5]|[0-4][0-9])" +
            "|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}" +
            "|(?:(2(5[0-5]|[0-4][0-9])" +
            "1[0-9][0-9]|[1-9]?[0-9])" +
            "|[a-z0-9-]*[a-z0-9]:" +
            "(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]" +
            "|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\]" +
            ")";

    private static final AccountResource2 ACCOUNT_RESOURCE = GWT.create(AccountResource2.class);

    private final RestFactory restFactory;

    private Account account;
    private Runnable onChangeHandler;
    private final Provider<ChangePasswordPresenter> changePasswordPresenterProvider;
    private String password;
    private String confirmPassword;

    @Inject
    public EditAccountPresenter(final EventBus eventBus,
                                final EditAccountView view,
                                final RestFactory restFactory,
                                final Provider<ChangePasswordPresenter> changePasswordPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
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
        getView().setEnabled(account.isEnabled());
        getView().setInactive(account.isInactive());
        getView().setLocked(account.isLocked());
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
        changePasswordPresenter.showSetPassword(getPasswordCaption(), (password, confirmPassword) -> {
            this.password = password;
            this.confirmPassword = confirmPassword;
        });
    }

    private void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            if (GwtNullSafe.isBlankString(getView().getUserId())) {
                AlertEvent.fireError(this, "A user id must be provided for the account.", e::reset);
            } else if (getView().getUserId().length() < 3) {
                AlertEvent.fireError(this, "A user id must be at least 3 characters.", e::reset);
            } else if (!GwtNullSafe.isBlankString(getView().getEmail()) &&
                    !RegExp.compile(EMAIL_PATTERN).test(getView().getEmail())) {
                AlertEvent.fireError(this, "Invalid email address.", e::reset);
            } else {
                if (account == null) {
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
                            .onSuccess(account -> {
                                onChangeHandler.run();
                                e.hide();
                            })
                            .onFailure(throwable ->
                                    AlertEvent.fireError(this, "Error creating account: "
                                            + throwable.getMessage(), e::reset))
                            .taskListener(this)
                            .exec();

                } else {
                    account.setUserId(getView().getUserId());
                    account.setEmail(getView().getEmail());
                    account.setFirstName(getView().getFirstName());
                    account.setLastName(getView().getLastName());
                    account.setComments(getView().getComments());
                    account.setNeverExpires(getView().isNeverExpires());
                    account.setEnabled(getView().isEnabled());
                    account.setInactive(getView().isInactive());
                    account.setLocked(getView().isLocked());

                    final UpdateAccountRequest request = new UpdateAccountRequest(account, password, confirmPassword);
                    restFactory
                            .create(ACCOUNT_RESOURCE)
                            .method(res -> res.update(request, account.getId()))
                            .onSuccess(account -> {
                                onChangeHandler.run();
                                e.hide();
                            })
                            .onFailure(throwable ->
                                    AlertEvent.fireError(this, "Error updating account: "
                                            + throwable.getMessage(), e::reset))
                            .taskListener(this)
                            .exec();
                }
            }
        } else {
            e.hide();
        }
    }


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

        void setInactive(boolean inactive);

        boolean isInactive();

        void setInactiveVisible(boolean visible);

        void setLocked(boolean locked);

        boolean isLocked();

        void setLockedVisible(boolean visible);

        void setPasswordButtonText(String text);
    }
}
