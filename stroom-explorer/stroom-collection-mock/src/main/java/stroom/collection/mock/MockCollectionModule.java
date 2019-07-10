package stroom.collection.mock;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.collection.api.CollectionService;
import stroom.docref.DocRef;

import java.util.Set;

public class MockCollectionModule extends AbstractModule {
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
}
