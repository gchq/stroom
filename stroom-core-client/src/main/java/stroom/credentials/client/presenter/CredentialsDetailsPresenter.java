package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.HasSave;
import stroom.credentials.client.view.CredentialsViewImpl;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsResponse.Status;
import stroom.credentials.shared.CredentialsType;
import stroom.dispatch.client.RestFactory;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;
import stroom.widget.spinner.client.SpinnerLarge;

import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

import javax.inject.Inject;

/**
 * Details view of Credentials.
 */
public class CredentialsDetailsPresenter
extends SimplePanel
implements HasHandlers, HasSave, TaskMonitorFactory {

    /** Points to top-level of this page. Needed for Alert dialogs */
    private CredentialsPresenter credentialsPresenter = null;

    /** Connection to the server */
    private final RestFactory restFactory;

    /** Current credentials */
    private Credentials credentials;

    /** Whether the data is dirty and needs saving */
    private boolean isDirty;

    /** Spinner for waiting */
    private final SpinnerLarge spinner = new SpinnerLarge();

    /** Save button */
    private final Button btnSave = new Button();

    /** Form group for the name */
    final FormGroup fmgName = new FormGroup();

    /** Name of the credentials */
    private final Label lblName = new Label();

    /** Form group of the type */
    private final FormGroup fmgType = new FormGroup();

    /** Type of the credentials */
    private final ListBox lstType = new ListBox();

    /** Form group for the username */
    private final FormGroup fmgUsername = new FormGroup();

    /** Widget for the username */
    private final TextBox txtUsername = new TextBox();

    /** Form group for the password */
    private final FormGroup fmgPassword = new FormGroup();

    /** Widget for the password */
    private final PasswordTextBox pwdPassword = new PasswordTextBox();

    /** Form group for the access token */
    private final FormGroup fmgAccessToken = new FormGroup();

    /** Widget for the access token */
    private final TextBox txtAccessToken = new TextBox();

    /** Form group for the passphrase */
    private final FormGroup fmgPassphrase = new FormGroup();

    /** Widget for the passphrase */
    private final TextBox txtPassphrase = new TextBox();

    /** Form group for the private key */
    private final FormGroup fmgPrivateKey = new FormGroup();

    /** Widget for the private key */
    private final TextArea txtPrivateKey = new TextArea();

    /** Used when we need an empty string */
    private static final String EMPTY = "";

    /**
     * Injected constructor.
     */
    @Inject
    public CredentialsDetailsPresenter(final RestFactory restFactory) {
        this.restFactory = restFactory;

        // Name of credentials
        fmgName.setLabel("Name");
        fmgName.add(lblName);

        // Setup the Type dropdown
        for (final CredentialsType type : CredentialsType.values()) {
            lstType.addItem(type.getDisplayName(), type.name());
        }
        lstType.setMultipleSelect(false);
        lstType.addChangeHandler(event -> setState());
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

        // Save button
        btnSave.setText("Save");
        btnSave.addClickHandler(event -> save());

        // Set the style of the spinner
        spinner.addStyleName("spinner-center");
        spinner.setVisible(false);

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
        pnlVertical.add(btnSave);
        pnlVertical.add(spinner);
        this.add(pnlVertical);

        // Dirty handlers
        final ChangeHandler dirtyChangeHandler = event -> onDirty();
        final ValueChangeHandler<String> dirtyValueChangeHandler = event -> onDirty();
        lstType.addChangeHandler(dirtyChangeHandler);
        txtUsername.addValueChangeHandler(dirtyValueChangeHandler);
        pwdPassword.addValueChangeHandler(dirtyValueChangeHandler);
        txtAccessToken.addValueChangeHandler(dirtyValueChangeHandler);
        txtPassphrase.addValueChangeHandler(dirtyValueChangeHandler);
        txtPrivateKey.addValueChangeHandler(dirtyValueChangeHandler);

        // Ensure initial state is correct
        setState();
    }

    /**
     * Called from CredentialsPresenter to hook stuff together.
     */
    public void setCredentialsPresenter(final CredentialsPresenter credentialsPresenter) {
        this.credentialsPresenter = credentialsPresenter;
    }

    /**
     * @return The selected credentials type as an enum.
     */
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

    /**
     * Updates the state of the UI.
     */
    private void setState() {
        if (credentials == null) {
            fmgName.setVisible(false);
            fmgType.setVisible(false);
            fmgUsername.setVisible(false);
            fmgPassword.setVisible(false);
            fmgAccessToken.setVisible(false);
            fmgPassphrase.setVisible(false);
            fmgPrivateKey.setVisible(false);

            btnSave.setVisible(false);

        } else {
            fmgName.setVisible(true);
            fmgType.setVisible(true);
            btnSave.setVisible(true);

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

    /**
     * Sets the credentials selected in the list view.
     * @param credentials The credentials to display and edit.
     */
    public void setCredentials(final Credentials credentials) {
        this.credentials = credentials;
        if (credentials == null) {
            lblName.setText(EMPTY);
            lstType.setSelectedIndex(0);
            txtUsername.setText(EMPTY);
            pwdPassword.setText(EMPTY);
            txtAccessToken.setText(EMPTY);
            txtPassphrase.setText(EMPTY);
            txtPrivateKey.setText(EMPTY);
        } else {
            lblName.setText(credentials.getName());
            lstType.setSelectedIndex(credentials.getType().ordinal());
            txtUsername.setText(credentials.getSecret().getUsername());
            pwdPassword.setText(credentials.getSecret().getPassword());
            txtAccessToken.setText(credentials.getSecret().getAccessToken());
            txtPassphrase.setText(credentials.getSecret().getPassphrase());
            txtPrivateKey.setText(credentials.getSecret().getPrivateKey());
        }

        // Just set so not dirty
        isDirty = false;

        // Update the state
        setState();
    }

    /**
     * Saves the data to the database.
     */
    @Override
    public void save() {
        CredentialsViewImpl.console("Saving credentials");
        credentials.setName(lblName.getText());
        credentials.setType(getCredentialsType());
        credentials.getSecret().setUsername(txtUsername.getText());
        credentials.getSecret().setPassword(pwdPassword.getText());
        credentials.getSecret().setAccessToken(txtAccessToken.getText());
        credentials.getSecret().setPassphrase(txtPassphrase.getText());
        credentials.getSecret().setPrivateKey(txtPrivateKey.getText());

        restFactory.create(CredentialsPresenter.CREDENTIALS_RESOURCE)
                .method(res -> res.store(credentials))
                .onSuccess(result -> {
                    CredentialsViewImpl.console("Saved credentials");
                    if (result.getStatus() == Status.OK) {
                        // All ok - don't need to display anything
                        isDirty = false;
                    } else {
                        CredentialsViewImpl.console("Error saving credentials");

                        AlertEvent.fireError(credentialsPresenter,
                                "Save Error",
                                result.getMessage(),
                                null);
                    }
                })
                .taskMonitorFactory(CredentialsDetailsPresenter.this)
                .exec();
    }

    /**
     * Called when something changes so the data is dirty and needs saving.
     */
    private void onDirty() {
        isDirty = true;
    }

    /**
     * @return true if the data is dirty and needs saving.
     */
    @Override
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * @return a task monitor for this panel.
     */
    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

}
