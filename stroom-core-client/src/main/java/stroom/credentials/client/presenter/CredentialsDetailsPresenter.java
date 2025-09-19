package stroom.credentials.client.presenter;

import stroom.credentials.client.view.CredentialsViewImpl;
import stroom.credentials.shared.CredentialsType;
import stroom.dispatch.client.RestFactory;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

import javax.inject.Inject;

public class CredentialsDetailsPresenter
extends SimplePanel
implements HasHandlers {

    /** Points to top-level of this page. Needed for Alert dialogs */
    private CredentialsPresenter credentialsPresenter = null;

    /** Connection to the server */
    private final RestFactory restFactory;

    /** Name of the credentials */
    private final Label lblName = new HTML();

    /** Type of the credentials */
    private final ListBox lstType = new ListBox();

    private final FormGroup fmgUsername = new FormGroup();
    private final TextBox txtUsername = new TextBox();

    private final FormGroup fmgPassword = new FormGroup();
    private final PasswordTextBox pwdPassword = new PasswordTextBox();

    private final FormGroup fmgAccessToken = new FormGroup();
    private final TextBox txtAccessToken = new TextBox();

    private final FormGroup fmgPassphrase = new FormGroup();
    private final TextBox txtPassphrase = new TextBox();

    private final FormGroup fmgPrivateKey = new FormGroup();
    private final TextArea txtPrivateKey = new TextArea();

    /**
     * Injected constructor.
     */
    @Inject
    public CredentialsDetailsPresenter(final RestFactory restFactory) {
        this.restFactory = restFactory;


        // Name of credentials
        // TODO remove this code
        lblName.setText("My Credentials Name");
        final FormGroup fmgName = new FormGroup();
        fmgName.setLabel("Name");
        fmgName.add(lblName);

        // Setup the Type dropdown
        for (final CredentialsType type : CredentialsType.values()) {
            lstType.addItem(type.getDisplayName(), type.name());
        }
        lstType.setMultipleSelect(false);
        lstType.addChangeHandler(event -> lstTypeHandler());
        final FormGroup fmgType = new FormGroup();
        fmgType.setLabel("Type");
        fmgType.add(lstType);

        // Username
        fmgUsername.setLabel("Username:");
        fmgUsername.add(txtUsername);

        // Password
        fmgPassword.setLabel("Password");
        fmgPassword.add(pwdPassword);

        // Access token
        fmgAccessToken.setLabel("Access token");
        fmgAccessToken.add(txtAccessToken);

        // Passphrase
        fmgPassphrase.setLabel("Passphrase");
        fmgPassphrase.add(txtPassphrase);

        // Private key
        fmgPrivateKey.setLabel("Private key");
        fmgPrivateKey.add(txtPrivateKey);

        // Vertical stack
        final VerticalPanel pnlVertical = new VerticalPanel();
        pnlVertical.addStyleName("credentials-details");
        pnlVertical.add(fmgName);
        pnlVertical.add(fmgType);
        pnlVertical.add(fmgUsername);
        pnlVertical.add(fmgPassword);
        pnlVertical.add(fmgAccessToken);
        pnlVertical.add(fmgPassphrase);
        pnlVertical.add(fmgPrivateKey);
        this.add(pnlVertical);
    }

    public void lstTypeHandler() {
        // Handle lstType changes
        setState();
    }

    private CredentialsType getCredentialsType() {
        final String selectedValue = lstType.getSelectedValue();
        try {
            return CredentialsType.valueOf(selectedValue);
        } catch (final IllegalArgumentException e) {
            // Ooops
            CredentialsViewImpl.console("lstType: Unknown value: " + selectedValue);
            return null;
        }
    }

    private void setState() {
        final CredentialsType type = getCredentialsType();
        if (type != null) {
            switch (type) {
                case USERNAME_PASSWORD -> {
                    fmgUsername.setVisible(true);
                    fmgPassword.setVisible(true);
                    fmgAccessToken.setVisible(false);
                    fmgPassphrase.setVisible(false);
                    fmgPrivateKey.setVisible(false);
                }
                case ACCESS_TOKEN -> {
                    fmgUsername.setVisible(false);
                    fmgPassword.setVisible(false);
                    fmgAccessToken.setVisible(true);
                    fmgPassphrase.setVisible(false);
                    fmgPrivateKey.setVisible(false);
                }
                case PRIVATE_CERT -> {
                    fmgUsername.setVisible(false);
                    fmgPassword.setVisible(false);
                    fmgAccessToken.setVisible(false);
                    fmgPassphrase.setVisible(true);
                    fmgPrivateKey.setVisible(true);
                }
            }
        }
    }

}
