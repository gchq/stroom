package stroom.activity.impl.mock;

import com.google.inject.AbstractModule;
import stroom.activity.api.CurrentActivity;
import stroom.activity.impl.mock.MockCurrentActivity;

public class MockActivityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CurrentActivity.class).to(MockCurrentActivity.class);
    }
}