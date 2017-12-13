package stroom.annotations.server;

import stroom.annotations.shared.AnnotationsIndex;
import stroom.annotations.shared.FindAnnotationsIndexCriteria;
import stroom.entity.server.DocumentEntityService;
import stroom.entity.server.FindService;

public interface AnnotationsIndexService
        extends DocumentEntityService<AnnotationsIndex>, FindService<AnnotationsIndex, FindAnnotationsIndexCriteria> {
}
