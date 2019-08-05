package stroom.search.db;

import com.google.common.base.Functions;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerDecorator;
import stroom.search.api.Searchable;
import stroom.search.api.SearchableProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
class SearchableProviderImpl implements SearchableProvider, ExplorerDecorator {
    private final Map<DocRef, Searchable> map;

    @Inject
    SearchableProviderImpl(final Set<Searchable> searchables) {
        this.map = searchables.stream().collect(Collectors.toMap(Searchable::getDocRef, Functions.identity()));
    }

    @Override
    public List<DocRef> list() {
        return map.values()
                .stream()
                .map(Searchable::getDocRef)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DocRef::getName))
                .collect(Collectors.toList());
    }

    @Override
    public Searchable get(final DocRef docRef) {
        return map.get(docRef);
    }
}
