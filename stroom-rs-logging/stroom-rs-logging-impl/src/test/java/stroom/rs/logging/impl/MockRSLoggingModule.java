package stroom.rs.logging.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.rs.logging.api.StroomServerLoggingFilter;

import com.google.inject.AbstractModule;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ResourceInfo;

public class MockRSLoggingModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RequestLoggingConfig.class).toInstance(new RequestLoggingConfig());
        bind(HttpServletRequest.class).to(MockHttpServletRequest.class);
        bind(ResourceInfo.class).to(MockResourceInfo.class);
    }
}

