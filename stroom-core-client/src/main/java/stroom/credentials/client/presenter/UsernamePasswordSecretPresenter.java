package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.UsernamePasswordSecretPresenter.UsernamePasswordSecretView;
import stroom.credentials.shared.Secret;
import stroom.credentials.shared.UsernamePasswordSecret;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

public class UsernamePasswordSecretPresenter
        extends MyPresenterWidget<UsernamePasswordSecretView> {

    @Inject
    public UsernamePasswordSecretPresenter(final EventBus eventBus,
                                           final UsernamePasswordSecretView view) {
        super(eventBus, view);
    }

    /**
     * Returns the secrets object held by this object.
     *
     * @return A new secrets object updated with any changes.
     */
    public Secret getSecret() {
        return new UsernamePasswordSecret(getView().getUsername(), getView().getPassword());
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
        if (getView().getUsername().isBlank()) {
            return "Username must not be empty";
        } else if (getView().getPassword().isBlank()) {
            return "Password must not be empty";
        }
        return null;
    }

    public interface UsernamePasswordSecretView extends View, Focus {

        String getUsername();

        void setUsername(String username);

        String getPassword();

        void setPassword(String password);
    }
}
