package stroom.search.extraction;

import stroom.query.api.v2.Query;
import stroom.search.coprocessor.Receiver;

public interface AnnotationsDecoratorFactory {
    Receiver create(Receiver receiver, Query query);
}
