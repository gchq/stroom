package stroom.search.extraction;

import stroom.query.api.v2.Query;

public interface AnnotationsDecoratorFactory {
    ExtractionReceiver create(ExtractionReceiver receiver, Query query);
}
