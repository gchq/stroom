/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.meta.impl;

import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.Status;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.ResultPage;
import stroom.util.time.TimePeriod;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MetaDao {

    Long getMaxId();

    /**
     * Get the current maximum id of any data with a create time less than or equal to the supplied time.
     *
     * @return The maximum id of any data item or null if there is no data.
     */
    Long getMaxId(long maxCreateTimeMs);

    Meta create(MetaProperties metaProperties);

    void search(ExpressionCriteria criteria, FieldIndex fieldIndex, ValuesConsumer consumer);

    int count(FindMetaCriteria criteria);

    /**
     * Find meta data records that match the specified criteria.
     *
     * @param criteria The criteria to find matching meta data records with.
     * @return A list of matching meta data records.
     */
    ResultPage<Meta> find(FindMetaCriteria criteria);

    /**
     * Find meta data for reprocessing where child records match the specified criteria.
     *
     * @param criteria The criteria to find matching meta data child records with.
     * @return A list of meta data for reprocessing where child records match the specified criteria.
     */
    ResultPage<Meta> findReprocess(FindMetaCriteria criteria);

    /**
     * Get a summary of the items included by the current selection.
     *
     * @param criteria The selection criteria.
     * @return An object that provides a summary of the current selection.
     */
    SelectionSummary getSelectionSummary(FindMetaCriteria criteria);

    /**
     * Get a summary of the parent items of the current selection for reprocessing purposes.
     *
     * @param criteria The selection criteria.
     * @return An object that provides a summary of the parent items of the current selection for reprocessing purposes.
     */
    SelectionSummary getReprocessSelectionSummary(FindMetaCriteria criteria);

    /**
     * A bulk update of status that uses a temporary table to avoid locks.
     */
    int updateStatus(FindMetaCriteria criteria,
                     Status currentStatus,
                     Status newStatus,
                     long statusTime,
                     boolean usesUniqueIds);

    /**
     * Physically delete the records from the database.
     */
    int delete(Collection<Long> metaIds);

    List<DataRetentionDeleteSummary> getRetentionDeletionSummary(DataRetentionRules rules,
                                                                 FindDataRetentionImpactCriteria criteria);

    /**
     * @param ruleActions Must be sorted with highest priority rule first
     * @param period
     */
    int logicalDelete(List<DataRetentionRuleAction> ruleActions,
                      TimePeriod period);

    int getLockCount();

    /**
     * Get a distinct list of processor UUIds for meta data matching the supplied criteria.
     *
     * @param criteria The criteria to find matching meta data processor UUIds for.
     * @return A distinct list of processor UUIds for meta data matching the supplied criteria.
     */
    List<String> getProcessorUuidList(FindMetaCriteria criteria);

    EffectiveMetaSet getEffectiveStreams(EffectiveMetaDataCriteria effectiveMetaDataCriteria);

    Set<Long> findLockedMeta(Collection<Long> metaIdCollection);

    /**
     * Get a batch of logically deleted {@link SimpleMeta} records that are older than {@code deleteThreshold}.
     * Gets a batch of the youngest ones matching that condition.
     */
    List<SimpleMeta> getLogicallyDeleted(Instant deleteThreshold,
                                         int batchSize,
                                         final Set<Long> metaIdExcludeSet);

    List<SimpleMeta> findBatch(final long minId,
                               final Long maxId,
                               final int batchSize);

    /**
     * Check if ids exist.
     *
     * @param ids A list of IDs to check the presence of
     * @return The sub-set of ids that exist in the database
     */
    Set<Long> exists(Set<Long> ids);
}
