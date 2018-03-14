package stroom.datafeed;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;
import stroom.dictionary.DictionaryStoreModule;
import stroom.docstore.memory.MemoryPersistenceModule;
import stroom.entity.MockEntityModule;
import stroom.feed.MockFeedModule;
import stroom.internalstatistics.MockMetaDataStatisticModule;
import stroom.properties.MockPropertyModule;
import stroom.ruleset.RulesetModule;
import stroom.security.MockSecurityContextModule;
import stroom.streamstore.MockStreamStoreModule;

public class TestBase {
    @Before
    public void setup() {
        final Injector injector = Guice.createInjector(
                new MockFeedModule(),
                new MockEntityModule(),
                new DataFeedModule(),
                new MockSecurityContextModule(),
                new RulesetModule(),
                new DictionaryStoreModule(),
                new MemoryPersistenceModule(),
                new MockMetaDataStatisticModule(),
                new MockPropertyModule(),
                new MockStreamStoreModule(),
                new MockFeedModule());
        injector.injectMembers(this);
    }
}
