package stroom.meta.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.AbstractField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface MetaDao extends Clearable {
    Long getMaxId();

    Meta create(MetaProperties metaProperties);

    ResultPage<Meta> find(FindMetaCriteria criteria);

    void search(ExpressionCriteria criteria, AbstractField[] fields, Consumer<Val[]> consumer);

    /**
     * Get a summary of the items included by the current selection.
     *
     * @param criteria The selection criteria.
     * @return An object that provides a summary of the current selection.
     */
    SelectionSummary getSelectionSummary(FindMetaCriteria criteria);

    Optional<Long> getMaxId(FindMetaCriteria criteria);

    int updateStatus(FindMetaCriteria criteria, Status currentStatus, Status newStatus, long statusTime);

    int delete(List<Long> metaIdList);

    int getLockCount();
}
