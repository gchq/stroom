package stroom.security.mock;

import stroom.util.guice.GuiceUtil;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;

import java.util.Collections;
import java.util.List;

public class MockHasUserDependenciesModule extends AbstractModule {

    @Override
    protected void configure() {

        GuiceUtil.buildMapBinder(binder(), String.class, HasUserDependencies.class)
                .addBinding(MockHasUserDependencies.class.getName(), MockHasUserDependencies.class);
    }


    // --------------------------------------------------------------------------------


    public static class MockHasUserDependencies implements HasUserDependencies {

        @Override
        public List<UserDependency> getUserDependencies(final UserRef userRef) {
            return Collections.emptyList();
        }
    }
}
