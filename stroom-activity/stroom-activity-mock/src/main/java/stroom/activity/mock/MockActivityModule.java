package stroom.activity.mock;

import com.google.inject.AbstractModule;
import stroom.activity.api.CurrentActivity;

public class MockActivityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CurrentActivity.class).to(MockCurrentActivity.class);
    }
}