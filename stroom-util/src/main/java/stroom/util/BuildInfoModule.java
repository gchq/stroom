package stroom.util;

import stroom.util.shared.BuildInfo;

import com.google.inject.AbstractModule;

public class BuildInfoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(BuildInfo.class).toProvider(BuildInfoProvider.class);
    }
}
