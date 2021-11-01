package stroom.ignite.impl;


import stroom.ignite.api.ApacheIgniteService;

import com.google.inject.AbstractModule;

public class ApacheIgniteModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ApacheIgniteService.class).to(ApacheIgniteServiceImpl.class);
    }
}
