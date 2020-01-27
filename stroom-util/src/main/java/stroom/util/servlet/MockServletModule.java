package stroom.util.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.util.Providers;

import javax.servlet.http.HttpServletRequest;

public class MockServletModule extends AbstractModule {
    @Override
    protected void configure() {
        final SessionIdProvider sessionIdProvider = () -> "mockSessionId";
        bind(HttpServletRequest.class).toProvider(HttpServletRequestHolder.class);
        bind(SessionIdProvider.class).toInstance(sessionIdProvider);
    }
}
