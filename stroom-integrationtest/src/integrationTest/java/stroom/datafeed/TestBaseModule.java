package stroom.datafeed;

import com.google.inject.AbstractModule;
import stroom.dictionary.DictionaryModule;
import stroom.docstore.memory.MemoryPersistenceModule;
import stroom.entity.MockEntityModule;
import stroom.feed.FeedModule;
import stroom.properties.impl.mock.MockPropertyModule;
import stroom.ruleset.RulesetModule;
import stroom.security.impl.mock.MockSecurityContextModule;
import stroom.data.store.impl.mock.MockStreamStoreModule;
import stroom.streamtask.statistic.MockMetaDataStatisticModule;

public class TestBaseModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new MockEntityModule());
        install(new DataFeedModule());
        install(new MockSecurityContextModule());
        install(new FeedModule());
        install(new RulesetModule());
        install(new DictionaryModule());
        install(new MemoryPersistenceModule());
        install(new MockMetaDataStatisticModule());
        install(new MockPropertyModule());
        install(new MockStreamStoreModule());
    }
}
