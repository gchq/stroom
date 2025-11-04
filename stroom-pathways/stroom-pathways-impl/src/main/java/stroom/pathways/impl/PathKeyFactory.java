package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.pathway.PathKey;

import java.util.List;

public interface PathKeyFactory {

    PathKey create(List<Span> spans);
}
