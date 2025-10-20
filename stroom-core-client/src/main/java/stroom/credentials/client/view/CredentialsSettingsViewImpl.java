package stroom.credentials.client.view;

import stroom.credentials.client.presenter.CredentialsDetailsDialogUiHandlers;
import stroom.credentials.client.presenter.CredentialsSettingsPresenter.CredentialsSettingsView;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;
import stroom.credentials.shared.CredentialsWithPerms;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

/**
 * Dialog showing all the information within Credentials, so user can add
 * or edit credentials.
 * Provides backing for CredentialsDetailsDialogView.ui.xml.
 */
public class CredentialsSettingsViewImpl
        extends ViewWithUiHandlers<CredentialsDetailsDialogUiHandlers>
        implements CredentialsSettingsView {

    /** Underlying Widget created by UiBinder */
    private final Widget widget;

    /** Underlying credentials object's UUID */
    private String uuid;

    @UiField
    FormGroup fmgName;

    @UiField
    TextBox txtName;

    @UiField
    FormGroup fmgType;

    @UiField
    ListBox lstType;

    @UiField
    FormGroup fmgCredsExpire;

    @UiField
    CustomCheckBox chkCredsExpire;

    @UiField
    FormGroup fmgExpires;

    @UiField
    MyDateBox dtpExpires;

    @UiField
    FormGroup fmgUsername;

    /** Accepts username for the Content Pack auth */
    @UiField
    TextBox txtUsername;

    @UiField
    FormGroup fmgPassword;

    /** Accepts password for the Content Pack auth */
    @UiField
    PasswordTextBox pwdPassword;

    @UiField
    FormGroup fmgAccessToken;

    @UiField
    TextBox txtAccessToken;

    @UiField
    FormGroup fmgPassphrase;

    @UiField
    TextBox txtPassphrase;

    @UiField
    FormGroup fmgPrivateKey;

    @UiField
    TextArea txtPrivateKey;

    @UiField
    FormGroup fmgServerPublicKey;

    @UiField
    TextArea txtServerPublicKey;

    /**
     * Used to store the permissions associated with credentials.
     */
    private CredentialsWithPerms credentialsWithPerms;

    /** Used when we need an empty string */
    private static final String EMPTY = "";

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public CredentialsSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        for (final CredentialsType type : CredentialsType.values()) {
            lstType.addItem(type.getDisplayName(), type.name());
        }
        lstType.setMultipleSelect(false);
        lstType.addChangeHandler(event -> updateState());
        chkCredsExpire.addValueChangeHandler(event -> updateState());
    }

    /**
     * @return The underlying widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Set the credentials into the dialog. Copies all data out of the
     * credentials object so no danger of overwriting the data in place.
     * @param cwp The credentials to display in the UI. Can be null
     *                    in which case empty values will be displayed.
     */
    @Override
    public void setCredentials(final CredentialsWithPerms cwp,
                               final CredentialsSecret secret) {

        // Store permissions for when we need to return them again
        credentialsWithPerms = cwp;

        if (cwp == null) {
            uuid = null;
            txtName.setText(EMPTY);
            lstType.setSelectedIndex(CredentialsType.USERNAME_PASSWORD.ordinal());
            chkCredsExpire.setValue(false);
            dtpExpires.setMilliseconds(0L);
            txtUsername.setText(EMPTY);
            pwdPassword.setText(EMPTY);
            txtAccessToken.setText(EMPTY);
            txtPassphrase.setText(EMPTY);
            txtPrivateKey.setText(EMPTY);
            txtServerPublicKey.setText(EMPTY);
        } else {
            final Credentials credentials = cwp.getCredentials();
            uuid = credentials.getUuid();
            txtName.setText(credentials.getName());
            lstType.setSelectedIndex(credentials.getType().ordinal());
            chkCredsExpire.setValue(credentials.isCredsExpire());
            dtpExpires.setMilliseconds(credentials.getExpires());
            txtUsername.setText(secret.getUsername());
            pwdPassword.setText(secret.getPassword());
            txtAccessToken.setText(secret.getAccessToken());
            txtPassphrase.setText(secret.getPassphrase());
            txtPrivateKey.setText(secret.getPrivateKey());
            txtServerPublicKey.setText(secret.getServerPublicKey());
        }
        updateState();
    }

    /**
     * Returns the credentialsWithPerms object held by this object,
     * updated with any changes.
     * @return A new credentialsWithPerms object containing a new Credentials object.
     */
    @Override
    public CredentialsWithPerms getCredentialsWithPerms() {

        final Long expiresAsLong = dtpExpires.getMilliseconds();
        final long expires = expiresAsLong == null
                ? 0L
                : expiresAsLong;
        final Credentials credentials = new Credentials(
                txtName.getText(),
                uuid,
                getCredentialsType(),
                chkCredsExpire.getValue(),
                expires);

        if (credentialsWithPerms != null) {
            return new CredentialsWithPerms(credentials,
                    credentialsWithPerms.isEdit(),
                    credentialsWithPerms.isDelete());
        } else {
            // Assume that we're allowed to do everything - enforced on the server anyway.
            return new CredentialsWithPerms(credentials);
        }
    }

    /**
     * Returns the secrets object held by this object.
     * @return A new secrets object updated with any changes.
     */
    @Override
    public CredentialsSecret getSecret() {
        return new CredentialsSecret(
                uuid,
                txtUsername.getText(),
                pwdPassword.getText(),
                txtAccessToken.getText(),
                txtPassphrase.getText(),
                txtPrivateKey.getText(),
                txtServerPublicKey.getText());
    }

    /**
     * @return true if the data in the dialog is valid, or false if not ok.
     */
    public boolean isValid() {
        return (getValidationMessage() == null);
    }

    /**
     * @return A validation message telling the user what is wrong, or null
     * if everything is ok.
     */
    public String getValidationMessage() {
        final CredentialsType type = getCredentialsType();

        if (type == null || uuid == null) {
            return "No credentials were set";
        } else if (txtName.getText().isBlank()) {
            return "The credentials must have a name";
        } else if (type == CredentialsType.USERNAME_PASSWORD && txtUsername.getText().isBlank()) {
            return "Username must not be empty";
        } else if (type == CredentialsType.ACCESS_TOKEN && txtAccessToken.getText().isBlank()) {
            return "Access token must not be empty";
        } else if (type == CredentialsType.PRIVATE_CERT) {
            // Passphrase might not be needed if key not encrypted
            if (txtPrivateKey.getText().isBlank()) {
                return "Private key must not be empty";
            }
        }

        return null;
    }

    /**
     * @return The selected credentials type as an enum.
     */
    private CredentialsType getCredentialsType() {
        final String selectedValue = lstType.getSelectedValue();
        try {
            return CredentialsType.valueOf(selectedValue);
        } catch (final IllegalArgumentException e) {
            // Ooops - unknown type. Return a safe value.
            return CredentialsType.USERNAME_PASSWORD;
        }
    }

    /**
     * Updates the state of the UI.
     */
    private void updateState() {
        if (uuid == null) {
            txtName.setText(EMPTY);
            lstType.setSelectedIndex(CredentialsType.USERNAME_PASSWORD.ordinal());
            chkCredsExpire.setValue(false);
            dtpExpires.setMilliseconds(0L);
            txtUsername.setText(EMPTY);
            pwdPassword.setText(EMPTY);
            txtAccessToken.setText(EMPTY);
            txtPassphrase.setText(EMPTY);
            txtPrivateKey.setText(EMPTY);

        } else {
            fmgName.setVisible(true);
            fmgType.setVisible(true);
            fmgCredsExpire.setVisible(true);

            // Expiry only visible if checkbox selected
            fmgExpires.setVisible(chkCredsExpire.getValue());

            final CredentialsType type = getCredentialsType();
            if (type != null) {
                switch (type) {
                    case USERNAME_PASSWORD -> {
                        fmgUsername.setVisible(true);
                        fmgPassword.setVisible(true);
                        fmgAccessToken.setVisible(false);
                        fmgPassphrase.setVisible(false);
                        fmgPrivateKey.setVisible(false);
                        fmgServerPublicKey.setVisible(false);
                    }
                    case ACCESS_TOKEN -> {
                        fmgUsername.setVisible(false);
                        fmgPassword.setVisible(false);
                        fmgAccessToken.setVisible(true);
                        fmgPassphrase.setVisible(false);
                        fmgPrivateKey.setVisible(false);
                        fmgServerPublicKey.setVisible(false);
                    }
                    case PRIVATE_CERT -> {
                        fmgUsername.setVisible(false);
                        fmgPassword.setVisible(false);
                        fmgAccessToken.setVisible(false);
                        fmgPassphrase.setVisible(true);
                        fmgPrivateKey.setVisible(true);
                        fmgServerPublicKey.setVisible(true);
                    }
                }
            }
        }
    }

    /**
     * Interface to keep GWT UiBinder happy.
     */
    public interface Binder extends UiBinder<Widget, CredentialsSettingsViewImpl> {
        // No code
    }
}
