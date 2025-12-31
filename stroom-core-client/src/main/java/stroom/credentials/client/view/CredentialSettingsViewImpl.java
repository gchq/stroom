package stroom.credentials.client.view;

import stroom.credentials.client.presenter.CredentialSettingsPresenter.CredentialSettingsView;
import stroom.credentials.client.presenter.CredentialSettingsUiHandlers;
import stroom.credentials.shared.CredentialType;
import stroom.item.client.SelectionBox;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import javax.inject.Inject;

/**
 * Dialog showing all the information within Credentials, so user can add
 * or edit credentials.
 * Provides backing for CredentialsDetailsDialogView.ui.xml.
 */
public class CredentialSettingsViewImpl
        extends ViewWithUiHandlers<CredentialSettingsUiHandlers>
        implements CredentialSettingsView {

    /**
     * Underlying Widget created by UiBinder
     */
    private final Widget widget;

    /**
     * Underlying credentials object's UUID
     */
    private String uuid;

    @UiField
    TextBox name;
    @UiField
    SelectionBox<CredentialType> credentialType;
    @UiField
    CustomCheckBox credsExpire;
    @UiField
    FormGroup expiryTimeFormGroup;
    @UiField
    DateTimeBox expiryTime;
    @UiField
    SimplePanel secret;

    /**
     * Injected constructor.
     */
    @Inject
    @SuppressWarnings("unused")
    public CredentialSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        for (final CredentialType type : CredentialType.values()) {
            credentialType.addItem(type);
        }
    }

    /**
     * @return The underlying widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getName() {
        return name.getValue();
    }

    @Override
    public void setName(final String name) {
        this.name.setValue(name);
    }

    @Override
    public CredentialType getCredentialType() {
        return credentialType.getValue();
    }

    @Override
    public void setCredentialType(final CredentialType credentialType) {
        this.credentialType.setValue(credentialType);
    }

    @Override
    public boolean isCredExpire() {
        return credsExpire.getValue();
    }

    @Override
    public void setCredExpire(final boolean expire) {
        this.credsExpire.setValue(expire);
        updateState();
    }

    @Override
    public DateTimeBox getExpiryTime() {
        return expiryTime;
    }

    @Override
    public void setSecretView(final View view) {
        secret.setWidget(view.asWidget());
    }

    /**
     * Updates the state of the UI.
     */
    private void updateState() {
        // Expiry only visible if checkbox selected
        expiryTimeFormGroup.setVisible(credsExpire.getValue());
    }

    @UiHandler("credentialType")
    public void onCredentialType(final ValueChangeEvent<CredentialType> e) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTypeChange(credentialType.getValue());
        }
    }

    @UiHandler("credsExpire")
    public void onCredsExpire(final ValueChangeEvent<Boolean> e) {
        updateState();
    }

    public interface Binder extends UiBinder<Widget, CredentialSettingsViewImpl> {

    }
}
