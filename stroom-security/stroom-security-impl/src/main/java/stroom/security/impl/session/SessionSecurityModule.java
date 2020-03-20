package stroom.security.impl.session;

import com.google.inject.AbstractModule;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.ServletBinder;

import javax.servlet.http.HttpSessionListener;

public class SessionSecurityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SessionListService.class).to(SessionListListener.class);

        GuiceUtil.buildMultiBinder(binder(), HttpSessionListener.class)
                .addBinding(SessionListListener.class);

        ServletBinder.create(binder())
                .bind(SessionListServlet.class);
    }
}
