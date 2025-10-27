package stroom.gitrepo.client.view;

import stroom.gitrepo.client.presenter.GitRepoCredentialsDialogPresenter;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

/**
 * Provides backing for CredentialsDetailsDialogView.ui.xml,
 * to provide the UI for the Dialog that allows users to enter authentication
 * information to obtain a Content Pack.
 */
public class GitRepoCredentialsDialogViewImpl
        extends ViewWithUiHandlers<GitRepoCredentialsDialogPresenter.GitRepoCredentialsDialogUiHandlers>
        implements GitRepoCredentialsDialogPresenter.GitRepoCredentialsDialogView {

    /** Underlying Widget created by UiBinder */
    private final Widget widget;

    /** Accepts username for the Content Pack auth */
    @UiField
    TextBox txtUsername;

    /** Accepts password for the Content Pack auth */
    @UiField
    PasswordTextBox pwdPassword;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public GitRepoCredentialsDialogViewImpl(final GitRepoCredentialsDialogViewImpl.Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    /**
     * @return The underlying widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Sets the username in the UI.
     */
    @Override
    public void setUsername(final String username) {
        txtUsername.setText(username);
    }

    /**
     * @return The username as entered into the Text Box.
     */
    @Override
    public String getUsername() {
        return txtUsername.getText();
    }

    /**
     * Sets the password in the UI.
     */
    @Override
    public void setPassword(final String password) {
        pwdPassword.setText(password);
    }

    /**
     * @return The password as entered into the Password Box.
     */
    @Override
    public String getPassword() {
        return pwdPassword.getText();
    }

    /**
     * Resets all dialog information for the next use.
     */
    @Override
    public void resetData() {
        txtUsername.setText("");
        pwdPassword.setText("");
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends
            UiBinder<Widget, GitRepoCredentialsDialogViewImpl> {
        // No code
    }

}

