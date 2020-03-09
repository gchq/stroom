package stroom.annotation.api;

import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.CreateEntryRequest;

public interface AnnotationCreator {
    AnnotationDetail createEntry(final CreateEntryRequest request);
}
