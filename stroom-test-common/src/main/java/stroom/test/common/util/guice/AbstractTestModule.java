package stroom.test.common.util.guice;

import com.google.inject.AbstractModule;

public class AbstractTestModule extends AbstractModule {

    /**
     * Binds clazz to a Mockito mock instance.
     *
     * @return The mock instance.
     */
    public <T> T bindMock(final Class<T> clazz) {
        return GuiceTestUtil.bindMock(binder(), clazz);
    }
}
