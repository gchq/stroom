package stroom.state.impl;

import stroom.pipeline.xsltfunctions.StateLookup;

import com.google.inject.AbstractModule;

public class MockStateModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(StateLookup.class).toInstance((docRef, lookupIdentifier, result) -> {

        });
    }
}
