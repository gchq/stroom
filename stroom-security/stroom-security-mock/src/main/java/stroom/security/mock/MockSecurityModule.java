package stroom.security.mock;

import stroom.security.api.AppPermissionService;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.HashFunctionFactory;
import stroom.security.api.UserService;
import stroom.test.common.util.guice.GuiceTestUtil;

import com.google.inject.AbstractModule;

public class MockSecurityModule extends AbstractModule {

    @Override
    protected void configure() {
        GuiceTestUtil.buildMockBinder(binder())
                .addMockBindingFor(UserService.class)
                .addMockBindingFor(DocumentPermissionService.class)
                .addMockBindingFor(AppPermissionService.class);

        bind(HashFunctionFactory.class).to(MockHashFunctionFactory.class);
    }
}
