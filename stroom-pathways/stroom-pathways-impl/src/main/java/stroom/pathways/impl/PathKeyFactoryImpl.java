package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.NamesPathKey;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.TerminalPathKey;

import java.util.List;

public class PathKeyFactoryImpl implements PathKeyFactory {

    @Override
    public PathKey create(final List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return TerminalPathKey.INSTANCE;
        } else if (spans.size() == 1) {
            return new NamePathKey(spans.getFirst().getName());
        }
        final List<String> names = spans.stream().map(Span::getName).toList();
        return new NamesPathKey(names);
    }
}
