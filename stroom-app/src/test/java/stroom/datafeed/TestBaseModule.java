package stroom.datafeed;

import com.google.inject.AbstractModule;
import stroom.data.meta.impl.mock.MockDataMetaModule;
import stroom.data.store.impl.fs.MockStreamStoreModule;
import stroom.dictionary.DictionaryModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.memory.MemoryPersistenceModule;
import stroom.feed.FeedModule;
import stroom.pipeline.scope.PipelineScopeModule;
import stroom.ruleset.RulesetModule;
import stroom.security.impl.mock.MockSecurityContextModule;
import stroom.processor.impl.db.statistic.MockMetaDataStatisticModule;

public class TestBaseModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new PipelineScopeModule());
        install(new DataFeedModule());
        install(new MockSecurityContextModule());
        install(new FeedModule());
        install(new RulesetModule());
        install(new DocStoreModule());
        install(new DictionaryModule());
        install(new MemoryPersistenceModule());
        install(new MockMetaDataStatisticModule());
//        install(new MockPropertyModule());
        install(new MockDataMetaModule());
        install(new MockStreamStoreModule());
    }
}
