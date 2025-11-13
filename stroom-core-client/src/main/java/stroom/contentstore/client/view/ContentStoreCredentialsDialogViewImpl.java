package stroom.contentstore.client.view;

import stroom.contentstore.client.presenter.ContentStoreCredentialsDialogPresenter;
import stroom.contentstore.client.presenter.ContentStoreCredentialsDialogUiHandlers;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
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
public class ContentStoreCredentialsDialogViewImpl
        extends ViewWithUiHandlers<ContentStoreCredentialsDialogUiHandlers>
        implements ContentStoreCredentialsDialogPresenter.ContentStoreCredentialsDialogView {

    /** Underlying Widget created by UiBinder */
    private final Widget widget;

    /** Introduces the credentials and tells users where to get the credentials from */
    @UiField
    HTML lblAuthContactHtml;

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
    public ContentStoreCredentialsDialogViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        lblAuthContactHtml.setWordWrap(true);
    }

    /**
     * @return The underlying widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Sets the markdown that tells users how to get credentials.
     * @param authContactHtml The HTML from markdown that provides info
     *                        for getting credentials for this content pack.
     */
    @Override
    public void setAuthContactHtml(final SafeHtml authContactHtml) {
        lblAuthContactHtml.setHTML(authContactHtml);
    }

    /**
     * @return The username as entered into the Text Box.
     */
    @Override
    public String getUsername() {
        return txtUsername.getText();
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
        lblAuthContactHtml.setHTML("");
        txtUsername.setText("");
        pwdPassword.setText("");
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends UiBinder<Widget, ContentStoreCredentialsDialogViewImpl> {
        // No code
    }
}
