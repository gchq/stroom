package stroom.searchable.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.searchable.api.Searchable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
class SpecialExplorerDataSourceImpl implements IsSpecialExplorerDataSource {

    // Class name => searchable impl
    private final Map<String, Provider<Searchable>> searchableProviders;

    @Inject
    SpecialExplorerDataSourceImpl(final Map<String, Provider<Searchable>> searchableProviders) {
        this.searchableProviders = searchableProviders;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return searchableProviders.entrySet()
                .stream()
                .map(entry -> {
                    final Searchable searchable = entry.getValue().get();
                    Objects.requireNonNull(searchable, () ->
                            "Provider returned null for Searchable class " + entry.getKey());
                    return searchable;
                })
                .map(Searchable::getDataSourceDocRefs)
                .flatMap(NullSafe::stream)
                .filter(Objects::nonNull)
                .toList();
    }
}
