package stroom.credentials.client.gin;

import stroom.credentials.client.CredentialsPlugin;
import stroom.credentials.client.presenter.CredentialsPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface CredentialsGinjector {

    AsyncProvider<CredentialsPlugin> getCredentialsPlugin();

    AsyncProvider<CredentialsPresenter> getCredentialsPresenter();

}
