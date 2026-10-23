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

package stroom.planb.impl.fs;

import stroom.planb.impl.dao.Db;
import stroom.planb.shared.PlanBDocument;

import java.io.IOException;

/**
 * What a store type does at the end of a merge into one of its archive buckets.
 *
 * <p>Every store type finishes a merge by letting the bucket rebuild whatever it derives from the
 * records it now holds — that is {@link Db#mergeComplete()}, and it is all {@link #DEFAULT} does.
 * A store type binds its own only where it has something further to do with what the merge found,
 * and then it owns making that call.
 *
 * <p>Called with the bucket still open and <b>before</b> it is published, on a local copy that is
 * discarded if this throws. So anything done here is retried with the bucket rather than stranded
 * ahead of it.
 *
 * <p>Bound per {@link stroom.planb.shared.StateType}, the way {@link MergeStrategy} is. A store type
 * with no binding gets {@link #DEFAULT}.
 */
@FunctionalInterface
public interface MergeCompletionStrategy {

    /** Completing the merge and nothing else, which is what most store types need. */
    MergeCompletionStrategy DEFAULT = (doc, db) -> db.mergeComplete();

    /**
     * @param doc the document whose bucket this is, carrying whatever settings the store type reads.
     * @param db  the open bucket, merged but not yet published.
     */
    void completeMerge(PlanBDocument doc, Db<?, ?> db) throws IOException;
}
