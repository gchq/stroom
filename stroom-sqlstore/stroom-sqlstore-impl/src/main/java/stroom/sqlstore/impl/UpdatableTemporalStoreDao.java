/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.sqlstore.shared.ChangeOperation;
import stroom.sqlstore.shared.TemporalStoreTimeRange;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface UpdatableTemporalStoreDao {

    /*
     * Every method is scoped by the UUID of the owning store document, never by map name.
     * Document names are mutable and not unique, so keying on the name meant a rename
     * orphaned every row and two same-named documents shared one dataset.
     */

    TemporalEntry create(String docUuid, TemporalEntry entry);

    TemporalEntry update(String docUuid, TemporalEntry entry);

    Optional<TemporalEntry> fetch(String docUuid, TemporalEntryId id);

    boolean delete(String docUuid, TemporalEntryId id);

    ResultPage<TemporalEntry> find(String docUuid, ExpressionCriteria criteria);

    /**
     * Returns one entry per key for the given store - the row with the greatest
     * {@code effective_time}, with no upper-time bound, so entries with effective times in
     * the future are included.
     *
     * <p>Deduplication is done in the database by a {@code MAX(effective_time)}-per-key
     * subquery joined back for the full row; only one row per key crosses the wire. It is
     * unpaged and ordered by key ascending. Used to back the "Show all" toggle in the Floor
     * Map Editor Fact List.</p>
     *
     * @param docUuid UUID of the owning store document; must not be {@code null} or blank
     * @return one entry per key, sorted by key ascending; never {@code null}, may be empty
     */
    List<TemporalEntry> fetchAll(String docUuid);

    /**
     * Returns the minimum and maximum {@code effective_time} values present in
     * the store for the given document.
     *
     * <p>Executes a single {@code SELECT MIN(effective_time), MAX(effective_time)}
     * aggregation — no deduplication is performed.</p>
     *
     * @param docUuid UUID of the owning store document; must not be {@code null} or blank
     * @return the time range; both fields are {@code null} if the store is empty
     */
    TemporalStoreTimeRange getTimeRange(String docUuid);

    /**
     * Applies a list of upsert and delete operations atomically within a
     * single database transaction.
     *
     * <p>Operations are applied strictly in list order. If any operation
     * fails the entire transaction is rolled back and no changes are
     * persisted. Every operation is scoped to {@code docUuid}, so an operation cannot
     * reach another document's data whatever map name its entry carries.</p>
     *
     * @param docUuid    UUID of the owning store document; must not be {@code null} or blank
     * @param operations ordered list of operations; must not be {@code null}
     * @throws RuntimeException (propagated from JOOQ) if any operation fails;
     *                          the transaction is rolled back automatically
     */
    void applyChanges(String docUuid, List<ChangeOperation> operations);

    void clear(String docUuid);

    long count(String docUuid);

    /**
     * Streams matching entries to the consumer.
     *
     * @param docUuid      UUID of the owning store document
     * @param criteria     filters and optional query time
     * @param includeValue whether the {@code value} column is wanted. It is a {@code longtext}, so
     *                     a caller that only needs keys and times - the timeline histogram, for one
     *                     - should pass {@code false} rather than have every row's payload read
     *                     from the database and thrown away. Entries then carry a {@code null}
     *                     value.
     * @param consumer     receives each entry
     */
    void search(String docUuid,
                ExpressionCriteria criteria,
                boolean includeValue,
                Consumer<TemporalEntry> consumer);
}
