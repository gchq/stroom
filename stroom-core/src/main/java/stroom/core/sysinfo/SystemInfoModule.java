package stroom.core.sysinfo;

import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class SystemInfoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SystemInfoService.class).to(SystemInfoServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(SystemInfoResourceImpl.class);
    }
}
