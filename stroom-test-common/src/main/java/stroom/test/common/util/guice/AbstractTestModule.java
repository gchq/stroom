package stroom.test.common.util.guice;

import com.google.inject.AbstractModule;
import org.mockito.Mockito;

public class AbstractTestModule extends AbstractModule {

    /**
     * Binds clazz to a Mockito mock instance.
     * @return The mock instance.
     */
    public <T> T bindMock(final Class<T> clazz) {
        T mock = Mockito.mock(clazz);
        bind(clazz)
                .toInstance(Mockito.mock(clazz));
        return mock;
    }
}
