package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.HasDataSourceDocRefs;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

public class ExplorerDecoratorImpl implements ExplorerDecorator {

    private final Set<HasDataSourceDocRefs> set;

    @Inject
    public ExplorerDecoratorImpl(final Set<HasDataSourceDocRefs> set) {
        this.set = set;
    }

    @Override
    public List<DocRef> list() {
        return set
                .stream()
                .flatMap(hasDataSourceDocRefs -> hasDataSourceDocRefs.getDataSourceDocRefs().stream())
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }
}
