package stroom.activity.mock;

import stroom.activity.api.CurrentActivity;

import com.google.inject.AbstractModule;

public class MockActivityModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CurrentActivity.class).to(MockCurrentActivity.class);
    }
}
