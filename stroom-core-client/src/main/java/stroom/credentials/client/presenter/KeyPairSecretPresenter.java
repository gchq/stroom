package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.KeyPairSecretPresenter.KeyPairSecretView;
import stroom.credentials.shared.KeyPairSecret;
import stroom.credentials.shared.Secret;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

public class KeyPairSecretPresenter
        extends MyPresenterWidget<KeyPairSecretView> {

    @Inject
    public KeyPairSecretPresenter(final EventBus eventBus,
                                  final KeyPairSecretView view) {
        super(eventBus, view);
    }

    /**
     * Returns the secrets object held by this object.
     *
     * @return A new secrets object updated with any changes.
     */
    public Secret getSecret() {
        return new KeyPairSecret(
                getView().getPassPhrase(),
                getView().getPrivateKey(),
                getView().getPublicKey());
    }

    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            consumer.accept(true);
        }
    }

    private String validate() {
        if (getView().getPrivateKey().isBlank()) {
            return "Private key must not be empty";
        }
        if (getView().getPublicKey().isBlank()) {
            return "Public key must not be empty";
        }
        return null;
    }

    public interface KeyPairSecretView extends View, Focus {

        String getPassPhrase();

        void setPassPhrase(String passPhrase);

        String getPrivateKey();

        void setPrivateKey(String privateKey);

        String getPublicKey();

        void setPublicKey(String publicKey);
    }
}
