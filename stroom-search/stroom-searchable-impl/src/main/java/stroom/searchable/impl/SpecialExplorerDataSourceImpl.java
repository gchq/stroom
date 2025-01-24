package stroom.searchable.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.searchable.api.Searchable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Singleton
class SpecialExplorerDataSourceImpl implements IsSpecialExplorerDataSource {

    private final Set<Searchable> searchables;

    @Inject
    SpecialExplorerDataSourceImpl(final Set<Searchable> searchables) {
        this.searchables = searchables;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return searchables.stream()
                .map(Searchable::getDocRef)
                .filter(Objects::nonNull)
                .toList();
    }
}
