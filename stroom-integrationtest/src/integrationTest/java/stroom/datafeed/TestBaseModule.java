package stroom.datafeed;

import com.google.inject.AbstractModule;
import stroom.dictionary.DictionaryModule;
import stroom.docstore.memory.MemoryPersistenceModule;
import stroom.entity.MockEntityModule;
import stroom.properties.MockPropertyModule;
import stroom.ruleset.RulesetModule;
import stroom.security.MockSecurityContextModule;
import stroom.streamstore.MockStreamStoreModule;
import stroom.streamtask.statistic.MockMetaDataStatisticModule;

public class TestBaseModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new MockEntityModule());
        install(new DataFeedModule());
        install(new MockSecurityContextModule());
        install(new RulesetModule());
        install(new DictionaryModule());
        install(new MemoryPersistenceModule());
        install(new MockMetaDataStatisticModule());
        install(new MockPropertyModule());
        install(new MockStreamStoreModule());
    }
}
