package stroom.streamstore.meta.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariConfig;
import stroom.entity.shared.Clearable;
import stroom.properties.StroomPropertyService;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.meta.api.StreamSecurityFilter;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Singleton;

public class StreamStoreMetaDbModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(FeedService.class).to(FeedServiceImpl.class);
        bind(StreamTypeService.class).to(StreamTypeServiceImpl.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(StreamMetaService.class).to(StreamMetaServiceImpl.class);
        bind(StreamSecurityFilter.class).to(StreamSecurityFilterImpl.class);


        bind(MetaKeyService.class).to(MetaKeyServiceImpl.class);
//        bind(MetaMapService.class).to(StreamDataRowService.class);
//        bind(MetaValueFlush.class).to(MetaValueFlushImpl.class);
        bind(MetaValueService.class).to(MetaValueServiceImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
//        clearableBinder.addBinding().to(MetaKeyServiceImpl.class);
        clearableBinder.addBinding().to(MetaValueServiceImpl.class);

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

    @Provides
    @Singleton
    MetaValueConfig metaValueConfig(final StroomPropertyService stroomPropertyService) {
        final String metaDatabaseAge = stroomPropertyService.getProperty("stroom.meta.deleteAge", "30d");
        final long deleteAge = ModelStringUtil.parseDurationString(metaDatabaseAge);
        final int deleteBatchSize = stroomPropertyService.getIntProperty("stroom.meta.deleteBatchSize", 1000);
        final int flushBatchSize = stroomPropertyService.getIntProperty("stroom.meta.flushBatchSize", 1000);
        final boolean addAsync = stroomPropertyService.getBooleanProperty("stroom.meta.addAsync", true);
        return new MetaValueConfig(deleteAge, deleteBatchSize, flushBatchSize, addAsync);
    }
}
