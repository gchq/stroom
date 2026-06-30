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

package stroom.planb.impl.db;

import stroom.planb.shared.PlanBDocument;

import java.nio.file.Path;

/**
 * Describes one document's locally-written pipeline output within a
 * {@link WrittenBatch}.
 *
 * <p>Each part carries a reference to its {@link PartDestination} — the
 * transfer strategy selected at write time based on the document's
 * configuration. Adding a new destination type (e.g. S3) requires only a new
 * {@link PartDestination} implementation and a routing decision in
 * {@link PlanBStreamWriter#getWriter}; no other classes need to change.
 *
 * @param localWriterDir   Absolute path to the local LMDB writer directory.
 * @param doc              The PlanB document this part belongs to.
 * @param shardIndex       Shard partition index, or {@link PlanBStreamWriter#UNSHARDED}.
 * @param synchroniseMerge Whether the merge processor should synchronise.
 * @param destination      The destination this part's data should be sent to.
 */
public record WrittenPart(
        Path localWriterDir,
        PlanBDocument doc,
        int shardIndex,
        boolean synchroniseMerge,
        PartDestination destination
) {}
