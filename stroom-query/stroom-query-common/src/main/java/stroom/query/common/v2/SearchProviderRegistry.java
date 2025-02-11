package stroom.query.common.v2;

import stroom.docref.DocRef;

import java.util.Optional;

public interface SearchProviderRegistry {

    Optional<SearchProvider> getSearchProvider(DocRef dataSourceRef);
}
