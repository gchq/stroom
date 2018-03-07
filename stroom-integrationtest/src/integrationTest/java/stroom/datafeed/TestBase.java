package stroom.datafeed;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.jpa.JpaPersistModule;
import org.junit.Before;
import stroom.dictionary.DictionaryModule;
import stroom.docstore.db.DBPersistence;
import stroom.docstore.db.DBPersistenceModule;
import stroom.docstore.memory.MemoryPersistenceModule;
import stroom.entity.EntityModule;
import stroom.feed.MockFeedModule;
import stroom.internalstatistics.MockMetaDataStatisticModule;
import stroom.properties.PropertyModule;
import stroom.ruleset.RulesetModule;
import stroom.security.MockSecurityContextModule;
import stroom.spring.PersistenceModule;
import stroom.streamstore.MockStreamStoreModule;

public class TestBase {
    @Before
    public void setup() {
        final Injector injector = Guice.createInjector(
                new MockFeedModule(),
                new EntityModule(),
                new DataFeedModule(),
                new MockSecurityContextModule(),
                new RulesetModule(),
                new DictionaryModule(),
                new MemoryPersistenceModule(),
                new MockMetaDataStatisticModule(),
                new PropertyModule(),
                new MockStreamStoreModule());
        injector.injectMembers(this);
    }
}
