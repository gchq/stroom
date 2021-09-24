package stroom.hazelcast.impl;

import stroom.hazelcast.api.HazelcastService;

import com.google.inject.AbstractModule;

public class HazelcastModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HazelcastService.class).to(HazelcastServiceImpl.class);
    }
}
