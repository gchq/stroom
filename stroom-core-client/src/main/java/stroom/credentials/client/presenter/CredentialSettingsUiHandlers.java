package stroom.credentials.client.presenter;

import stroom.credentials.shared.CredentialType;

import com.gwtplatform.mvp.client.UiHandlers;

public interface CredentialSettingsUiHandlers extends UiHandlers {
    void onTypeChange(CredentialType type);
}
