package stroom.app.guice;

import stroom.app.uri.UriFactoryModule;
import stroom.cluster.impl.ClusterModule;
import stroom.dropwizard.common.FilteredHealthCheckServlet;
import stroom.dropwizard.common.LogLevelInspector;
import stroom.index.impl.IndexShardWriterExecutorProvider;
import stroom.index.impl.IndexShardWriterExecutorProviderImpl;
import stroom.lifecycle.impl.LifecycleServiceModule;
import stroom.meta.statistics.impl.MetaStatisticsModule;
import stroom.resource.impl.SessionResourceModule;
import stroom.security.impl.SecurityContextModule;
import stroom.statistics.impl.sql.search.SQLStatisticSearchModule;
import stroom.util.guice.AdminServletBinder;
import stroom.util.guice.HasSystemInfoBinder;

import com.google.inject.AbstractModule;

public class AppModule extends AbstractModule {

    @Override
    protected void configure() {

        install(new UriFactoryModule());
        install(new CoreModule());
        install(new LifecycleServiceModule());
        install(new JobsModule());
        install(new ClusterModule());
        install(new SecurityContextModule());
        install(new MetaStatisticsModule());
        install(new SQLStatisticSearchModule());
        install(new SessionResourceModule());
        install(new JerseyModule());
        bind(IndexShardWriterExecutorProvider.class).to(IndexShardWriterExecutorProviderImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(LogLevelInspector.class);

        // Servlets on the admin path/port
        AdminServletBinder.create(binder())
                .bind(FilteredHealthCheckServlet.class);
    }
}
