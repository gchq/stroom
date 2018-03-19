package stroom.datafeed;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;

public class TestBase {
    @Before
    public void setup() {
        final Injector injector = Guice.createInjector(new TestBaseModule());
        injector.injectMembers(this);
    }
}
