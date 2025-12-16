package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.AccessTokenSecretPresenter.AccessTokenSecretView;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.Secret;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

public class AccessTokenSecretPresenter
        extends MyPresenterWidget<AccessTokenSecretView> {

    @Inject
    public AccessTokenSecretPresenter(final EventBus eventBus,
                                      final AccessTokenSecretView view) {
        super(eventBus, view);
    }

    /**
     * Returns the secrets object held by this object.
     *
     * @return A new secrets object updated with any changes.
     */
    public Secret getSecret() {
        return new AccessTokenSecret(getView().getAccessToken());
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
        if (getView().getAccessToken().isBlank()) {
            return "Access token must not be empty";
        }
        return null;
    }

    public interface AccessTokenSecretView extends View, Focus {

        String getAccessToken();

        void setAccessToken(String accessToken);
    }
}
