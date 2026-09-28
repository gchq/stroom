package stroom.credentials.client.presenter;

import stroom.ai.shared.KeyStoreType;
import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialSettingsPresenter.CredentialSettingsView;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.Secret;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimePopup;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

public class CredentialSettingsPresenter
        extends MyPresenterWidget<CredentialSettingsView>
        implements CredentialSettingsUiHandlers {

    private final AccessTokenSecretPresenter accessTokenSecretPresenter;
    private final SshKeySecretPresenter sshKeySecretPresenter;
    private final KeyStoreSecretPresenter keyStoreSecretPresenter;
    private final UsernamePasswordSecretPresenter usernamePasswordSecretPresenter;

    private DocRef docRef;

    @Inject
    public CredentialSettingsPresenter(final EventBus eventBus,
                                       final CredentialSettingsView view,
                                       final AccessTokenSecretPresenter accessTokenSecretPresenter,
                                       final SshKeySecretPresenter sshKeySecretPresenter,
                                       final KeyStoreSecretPresenter keyStoreSecretPresenter,
                                       final UsernamePasswordSecretPresenter usernamePasswordSecretPresenter,
                                       final Provider<DateTimePopup> dateTimePopupProvider) {
        super(eventBus, view);
        view.setUiHandlers(this);
        view.getExpiryTime().setPopupProvider(dateTimePopupProvider);

        this.accessTokenSecretPresenter = accessTokenSecretPresenter;
        this.sshKeySecretPresenter = sshKeySecretPresenter;
        this.keyStoreSecretPresenter = keyStoreSecretPresenter;
        this.usernamePasswordSecretPresenter = usernamePasswordSecretPresenter;
    }

    @Override
    public void onTypeChange(final CredentialType type) {
        final View secretView = getSecretView(type);
        getView().setSecretView(secretView);
    }

    private SecretPresenter<?> getSecretPresenter(final CredentialType credentialType) {
        return switch (credentialType) {
            case ACCESS_TOKEN -> accessTokenSecretPresenter;
            case KEY_STORE -> keyStoreSecretPresenter;
            case SSH_KEY -> sshKeySecretPresenter;
            case USERNAME_PASSWORD -> usernamePasswordSecretPresenter;
        };
    }

    private View getSecretView(final CredentialType credentialType) {
        return getSecretPresenter(credentialType)
                .getView();
    }

    /**
     * Set the credentials into the dialog. Copies all data out of the
     * credentials object so no danger of overwriting the data in place.
     *
     * @param cwp The credentials to display in the UI. Can be null
     *            in which case empty values will be displayed.
     */
    public void read(final DocRef docRef,
                     final CredentialWithPerms cwp) {
        this.docRef = docRef;
        if (cwp == null) {
            getView().setName("");
            getView().setCredExpire(false);
            getView().getExpiryTime().setValue(System.currentTimeMillis());
            getView().setCredentialType(CredentialType.USERNAME_PASSWORD);
            keyStoreSecretPresenter.setType(KeyStoreType.PKCS12);
        } else {
            final Credential credential = cwp.getCredential();
            getView().setName(credential.getName());
            getView().setCredExpire(credential.getExpiryTimeMs() != null);
            if (credential.getExpiryTimeMs() != null) {
                getView().getExpiryTime().setValue(credential.getExpiryTimeMs());
            } else {
                getView().getExpiryTime().setValue(System.currentTimeMillis());
            }
            final CredentialType credentialType = credential.getCredentialType();
            getView().setCredentialType(credentialType);
            keyStoreSecretPresenter.setType(NullSafe.getOrElse(
                    credential,
                    Credential::getKeyStoreType,
                    KeyStoreType.PKCS12));
        }

        onTypeChange(getView().getCredentialType());
    }

    private Secret getSecret() {
        return getSecretPresenter(getView().getCredentialType()).getSecret();
    }

    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            getSecretPresenter(getView().getCredentialType()).onOk(consumer);
        }
    }

    private String validate() {
        if (NullSafe.isBlankString(getView().getName())) {
            return "Name cannot be empty";
        }
        if (getView().getCredentialType() == null) {
            return "You must specify a credential type";
        }
        if (getView().isCredExpire() && !getView().getExpiryTime().isValid()) {
            return "You must set a valid expiry time";
        }
        return null;
    }

    public PutCredentialRequest write() {
        final Secret secret = getSecret();
        final KeyStoreType keyStoreType;
        if (secret instanceof final KeyStoreSecret keyStoreSecret) {
            keyStoreType = keyStoreSecret.getKeyStoreType();
        } else {
            keyStoreType = null;
        }

        final Credential credential = new Credential(
                docRef.getUuid(),
                getView().getName(),
                null,
                null,
                null,
                null,
                getView().getCredentialType(),
                keyStoreType,
                getView().isCredExpire()
                        ? getView().getExpiryTime().getValue()
                        : null);

        return new PutCredentialRequest(credential, secret);
    }


    // --------------------------------------------------------------------------------


    /**
     * View that this presents. Provides access to the data in the UI.
     */
    public interface CredentialSettingsView extends View, HasUiHandlers<CredentialSettingsUiHandlers> {

        String getName();

        void setName(String name);

        CredentialType getCredentialType();

        void setCredentialType(CredentialType credentialType);

        boolean isCredExpire();

        void setCredExpire(boolean expire);

        DateTimeBox getExpiryTime();

        void setSecretView(View view);
    }
}
