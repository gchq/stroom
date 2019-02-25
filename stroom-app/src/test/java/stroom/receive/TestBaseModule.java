package stroom.receive;

import com.google.inject.AbstractModule;
import stroom.cache.impl.CacheModule;
import stroom.data.store.impl.mock.MockStreamStoreModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.memory.MemoryPersistenceModule;
import stroom.feed.impl.FeedModule;
import stroom.meta.impl.mock.MockMetaModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.util.pipeline.scope.PipelineScopeModule;
import stroom.receive.rules.impl.ReceiveDataRulesetModule;
import stroom.security.impl.mock.MockSecurityContextModule;

public class TestBaseModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new CacheModule());
        install(new PipelineScopeModule());
        install(new ReceiveDataModule());
        install(new MockSecurityContextModule());
        install(new FeedModule());
        install(new ReceiveDataRulesetModule());
        install(new DocStoreModule());
        install(new DictionaryModule());
        install(new MemoryPersistenceModule());
        install(new MockMetaStatisticsModule());
        install(new MockMetaModule());
        install(new MockStreamStoreModule());
    }
}
