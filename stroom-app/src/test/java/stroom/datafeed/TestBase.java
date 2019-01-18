package stroom.datafeed;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;


public class TestBase {
    @BeforeEach
    void setup() {
        final Injector injector = Guice.createInjector(new TestBaseModule());
        injector.injectMembers(this);
    }
}
