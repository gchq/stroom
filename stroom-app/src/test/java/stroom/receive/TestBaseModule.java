package stroom.receive;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.cache.impl.CacheModule;
import stroom.collection.api.CollectionService;
import stroom.collection.mock.MockCollectionModule;
import stroom.docref.DocRef;
import stroom.receive.common.RemoteFeedModule;
import stroom.core.receive.ReceiveDataModule;
import stroom.data.store.mock.MockStreamStoreModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.memory.MemoryPersistenceModule;
import stroom.feed.impl.FeedModule;
import stroom.meta.mock.MockMetaModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.receive.rules.impl.ReceiveDataRulesetModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.TaskContext;
import stroom.util.pipeline.scope.PipelineScopeModule;

import java.util.Collections;
import java.util.Set;

public class TestBaseModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new CacheModule());
        install(new DictionaryModule());
        install(new DocStoreModule());
        install(new FeedModule());
        install(new MemoryPersistenceModule());
        install(new MockMetaModule());
        install(new MockMetaStatisticsModule());
        install(new MockSecurityContextModule());
        install(new MockStreamStoreModule());
        install(new PipelineScopeModule());
        install(new ReceiveDataModule());
        install(new ReceiveDataRulesetModule());
        install(new MockCollectionModule());

        bind(TaskContext.class).to(SimpleTaskContext.class);
    }
}
