package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsModule;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserGroupsService;
import stroom.security.mock.MockSecurityContext;
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

        bind(SecurityContext.class).to(MockSecurityContext.class);
        bind(DocumentPermissionService.class).to(MockDocumentPermissionService.class);
        bind(UserGroupsService.class).to(MockUserGroupsService.class);
    }
}
