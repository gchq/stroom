package stroom.credentials.impl;

import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.CredentialsResource;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class CredentialsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(StoredSecrets.class).to(CredentialsService.class);
        bind(CredentialsResource.class).to(CredentialsResourceImpl.class);
        RestResourcesBinder.create(binder()).bind(CredentialsResourceImpl.class);
    }
}
