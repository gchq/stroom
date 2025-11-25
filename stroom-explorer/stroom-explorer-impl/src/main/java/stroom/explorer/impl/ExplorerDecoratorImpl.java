package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ExplorerDecoratorImpl implements ExplorerDecorator {

    private final Set<IsSpecialExplorerDataSource> set;

    @Inject
    public ExplorerDecoratorImpl(final Set<IsSpecialExplorerDataSource> set) {
        this.set = set;
    }

    @Override
    public List<DocRef> list() {
        return NullSafe.stream(set)
                .flatMap(hasDataSourceDocRefs ->
                        NullSafe.stream(hasDataSourceDocRefs.getDataSourceDocRefs()))
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }
}
