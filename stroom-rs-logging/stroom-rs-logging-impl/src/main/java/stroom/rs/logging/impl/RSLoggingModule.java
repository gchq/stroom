package stroom.rs.logging.impl;

import stroom.rs.logging.api.StroomServerLoggingFilter;

import com.google.inject.AbstractModule;

public class RSLoggingModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StroomServerLoggingFilter.class).to(StroomServerLoggingFilterImpl.class);
        bind(RequestEventLog.class).to(RequestEventLogImpl.class);
    }
}
