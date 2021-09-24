package stroom.util.io;

import com.google.inject.AbstractModule;

public class DirProvidersModule extends AbstractModule {

    @Override
    protected void configure() {
        requireBinding(PathConfig.class);

        bind(HomeDirProvider.class).to(HomeDirProviderImpl.class);
        bind(TempDirProvider.class).to(TempDirProviderImpl.class);
    }
}
