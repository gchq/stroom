package stroom.security.identity.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

public interface LoginUiHandlers extends UiHandlers {
    void signIn();

    void emailResetPassword();
}
