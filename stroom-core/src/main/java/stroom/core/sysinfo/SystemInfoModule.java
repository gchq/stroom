package stroom.core.sysinfo;

import stroom.util.guice.GuiceUtil;
import stroom.util.shared.RestResource;

import com.google.inject.AbstractModule;

public class SystemInfoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SystemInfoService.class).to(SystemInfoServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(SystemInfoResourceImpl.class);
    }
}
