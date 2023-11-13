package stroom.searchable.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.HasDataSourceDocRefs;
import stroom.searchable.api.Searchable;
import stroom.searchable.api.SearchableProvider;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class SearchableProviderImpl implements SearchableProvider, HasDataSourceDocRefs {

    private final Set<Searchable> searchables;

    @Inject
    SearchableProviderImpl(final Set<Searchable> searchables) {
        this.searchables = searchables;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
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

    @Override
    public List<DocRef> list() {
        return searchables.stream().map(Searchable::getDocRef).toList();
    }
}
