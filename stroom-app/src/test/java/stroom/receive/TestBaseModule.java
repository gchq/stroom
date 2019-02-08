package stroom.receive;

import com.google.inject.AbstractModule;
import stroom.cache.impl.CacheModule;
import stroom.meta.impl.mock.MockMetaModule;
import stroom.data.store.impl.mock.MockStreamStoreModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.memory.MemoryPersistenceModule;
import stroom.pipeline.feed.FeedModule;
import stroom.pipeline.scope.PipelineScopeModule;
import stroom.ruleset.ReceiveDataRulesetModule;
import stroom.security.impl.mock.MockSecurityContextModule;
import stroom.streamtask.statistic.MockMetaDataStatisticModule;

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
        install(new MockMetaDataStatisticModule());
//        install(new MockPropertyModule());
        install(new MockMetaModule());
        install(new MockStreamStoreModule());
    }
}
