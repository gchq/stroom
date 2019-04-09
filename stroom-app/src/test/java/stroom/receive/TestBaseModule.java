package stroom.receive;

import com.google.inject.AbstractModule;
import stroom.cache.impl.CacheModule;
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

        bind(TaskContext.class).to(SimpleTaskContext.class);
    }
}
