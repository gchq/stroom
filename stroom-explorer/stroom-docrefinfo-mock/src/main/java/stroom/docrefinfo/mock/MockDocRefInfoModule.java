package stroom.docrefinfo.mock;

import stroom.docrefinfo.api.DocRefInfoService;

import com.google.inject.AbstractModule;

public class MockDocRefInfoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DocRefInfoService.class).to(MockDocRefInfoService.class);
    }
}
