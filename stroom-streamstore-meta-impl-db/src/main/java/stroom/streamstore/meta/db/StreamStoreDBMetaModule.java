package stroom.streamstore.meta.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.meta.api.StreamSecurityFilter;

import javax.inject.Singleton;

public class StreamStoreDBMetaModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(FeedService.class).to(FeedServiceImpl.class);
        bind(StreamTypeService.class).to(StreamTypeServiceImpl.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(StreamMetaService.class).to(StreamMetaServiceImpl.class);
        bind(StreamSecurityFilter.class).to(StreamSecurityFilterImpl.class);
    }

    @Provides
    @Singleton
    StreamMetaDataSource getStreamMetaDataSource() {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8");
        config.setUsername("stroomuser");
        config.setPassword("stroompassword1");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new StreamMetaDataSource(config);
    }
}
