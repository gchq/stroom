package stroom.meta.impl;

import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.Status;
import stroom.util.shared.Clearable;

import java.util.List;
import java.util.Optional;

public interface MetaDao extends Clearable {
    Long getMaxId();

    Meta create(MetaProperties metaProperties);

    List<Meta> find(FindMetaCriteria criteria);

    Optional<Long> getMaxId(FindMetaCriteria criteria);

    int updateStatus(FindMetaCriteria criteria, Status newStatus, Status currentStatus, long statusTime);

    int delete(FindMetaCriteria criteria);

    int getLockCount();
}
