package stroom.streamstore.meta.db;

import stroom.entity.shared.BaseResultList;
import stroom.streamstore.OldFindStreamCriteria;
import stroom.streamstore.shared.Stream;

public interface StreamEntityService {
    // OLD WAY OF QUERYING
    @Deprecated
    BaseResultList<Stream> find(OldFindStreamCriteria findStreamCriteria);
}
