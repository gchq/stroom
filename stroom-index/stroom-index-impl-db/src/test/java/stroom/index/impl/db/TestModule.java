package stroom.index.impl.db;

import com.google.inject.AbstractModule;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.db.ForceCoreMigration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModule extends AbstractModule {
    static final String TEST_USER = "testUser";

    @Override
    protected void configure() {
        install(new DbTestModule());

        // Create a test security context
        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserId()).thenReturn(TEST_USER);
        bind(SecurityContext.class).toInstance(securityContext);
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() { });
    }
}
