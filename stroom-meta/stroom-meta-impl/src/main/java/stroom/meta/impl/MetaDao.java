package stroom.meta.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.data.retention.shared.DataRetentionRuleAction;
import stroom.datasource.api.v2.AbstractField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;
import stroom.util.time.TimePeriod;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface MetaDao extends Clearable {
    Long getMaxId();

    Meta create(MetaProperties metaProperties);

    void create(List<MetaProperties> metaPropertiesList, Status status);

    ResultPage<Meta> find(FindMetaCriteria criteria);

    int count(FindMetaCriteria criteria);

    void search(ExpressionCriteria criteria, AbstractField[] fields, Consumer<Val[]> consumer);

    Optional<Long> getMaxId(FindMetaCriteria criteria);

    int updateStatus(FindMetaCriteria criteria, Status currentStatus, Status newStatus, long statusTime);

    int delete(List<Long> metaIdList);

    /**
     * @param ruleActions Must be sorted with highest priority rule first
     * @param period
     */
    int logicalDelete(final List<DataRetentionRuleAction> ruleActions,
                      final TimePeriod period,
                      int batchSize);

    int getLockCount();
}
