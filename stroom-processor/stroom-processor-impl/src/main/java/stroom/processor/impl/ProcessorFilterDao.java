/*
 * Copyright 2019 Crown Copyright
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

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {

    Optional<ProcessorFilter> fetchByUuid(String uuid);

    ResultPage<ProcessorFilter> find(ExpressionCriteria criteria);

    int logicalDeleteByProcessorFilterId(int processorFilterId);

    /**
     * Logically delete COMPLETE processor filters with no outstanding tasks where the tracker last poll is older
     * than the threshold. Note that COMPLETE just means that we have finished producing tasks on the DB, but we
     * can't delete the filter until all associated tasks have been processed otherwise they will never be picked
     * up.
     *
     * @param deleteThreshold Only logically delete filters with a last poll time older than the threshold.
     * @return The number of logically deleted filters.
     */
    int logicallyDeleteOldProcessorFilters(Instant deleteThreshold);

    /**
     * Bring a logically deleted filter back into use, by <b>replacing it with a replica</b>: a new
     * filter with a new id and a fresh tracker that takes over the deleted filter's uuid and
     * settings, and records it as its parent. The deleted filter keeps its tasks and its history
     * and stays deleted, with a new uuid of its own so the unique key still holds.
     * <p>
     * It replaces rather than resets because a filter id has to keep meaning the same body of
     * work. Resetting a tracker in place changes what an id means without changing the id, which
     * silently invalidates everything keyed by filter id - the per node availability summary,
     * {@code FilterFetchBackoff}, {@code ProcessorProfileCache} - with no signal that would tell
     * the other nodes in the cluster to throw their copies away. It also left retained COMPLETE
     * tasks pointing at a tracker claiming the filter had never run. A new id invalidates all of
     * that naturally, and is what reprocessing already does
     * ({@code ProcessorFilterServiceImpl.reprocess}).
     *
     * @param processorFilter The deleted filter to replace. Returned unchanged if it is not
     *                        actually deleted.
     * @return The replica, or the supplied filter if there was nothing to do.
     */
    ProcessorFilter restoreProcessorFilter(ProcessorFilter processorFilter);

    /**
     * Physically delete old processor filters that are logically deleted with an update time older than the threshold.
     *
     * @param deleteThreshold Only physically delete filters with an update time older than the threshold.
     * @return The number of physically deleted filters.
     */
    Set<String> physicalDeleteOldProcessorFilters(Instant deleteThreshold);

    List<ProcessorFilter> fetchByRunAsUser(final String userUuid);
}
