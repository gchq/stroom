package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsModule;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;

public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        install(new CredentialsDaoModule());
        install(new CredentialsDbModule());
        install(new CredentialsModule());
        install(new DbTestModule());
    }
}
