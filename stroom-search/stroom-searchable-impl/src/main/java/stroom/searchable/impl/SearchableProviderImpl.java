package stroom.searchable.impl;

import org.springframework.stereotype.Component;
import stroom.explorer.api.ExplorerDecorator;
import stroom.query.api.v2.DocRef;
import stroom.searchable.api.Searchable;
import stroom.searchable.api.SearchableProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Singleton
class SearchableProviderImpl implements SearchableProvider, ExplorerDecorator {
    private final Set<Searchable> searchables;

    @Inject
    SearchableProviderImpl(final Set<Searchable> searchables) {
        this.searchables = searchables;
    }

    @Override
    public List<DocRef> list() {
        return searchables.stream()
                .map(Searchable::getDocRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Searchable get(final DocRef docRef) {
        return searchables.stream()
                .filter(searchable -> Objects.equals(searchable.getDocRef(), docRef))
                .findFirst()
                .orElse(null);
    }
}
