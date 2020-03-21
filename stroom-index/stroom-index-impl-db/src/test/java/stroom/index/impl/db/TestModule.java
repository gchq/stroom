package stroom.index.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.db.ForceCoreMigration;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModule extends AbstractModule {
    static final String TEST_USER = "testUser";

    @Override
    protected void configure() {
        install(new DbTestModule());

        // Create a test security context
        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserId()).thenReturn(TEST_USER);
        bind(SecurityContext.class).toInstance(securityContext);
        bind(ForceCoreMigration.class).toInstance(new ForceCoreMigration() { });
    }

    @Provides
    CollectionService collectionService() {
        return new CollectionService() {
            @Override
            public Set<DocRef> getChildren(final DocRef folder, final String type) {
                return null;
            }

            @Override
            public Set<DocRef> getDescendants(final DocRef folder, final String type) {
                return null;
            }
        };
    }

    @Provides
    WordListProvider wordListProvider() {
        return new WordListProvider() {
            @Override
            public List<DocRef> findByName(final String dictionaryName) {
                return Collections.emptyList();
            }

            @Override
            public String getCombinedData(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public String[] getWords(final DocRef dictionaryRef) {
                return null;
            }
        };
    }
}
