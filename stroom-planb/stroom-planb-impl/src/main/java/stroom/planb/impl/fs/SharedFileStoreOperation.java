/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.planb.shared.PlanBDocument;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Contract for a maintenance operation that runs inside the per-shard cluster
 * lock during a merge cycle.
 *
 * <p>{@link #isDue} is called <em>before</em> acquiring the lock (read-only, safe
 * to race) so the lock is only acquired when there is work to do.
 * {@link #run} is called <em>inside</em> the lock with the shard open.</p>
 */
public interface SharedFileStoreOperation {

    /**
     * Returns {@code true} if this operation should run for the given shard.
     * Must be cheap and read-only — called before the cluster lock is held.
     */
    boolean isDue(PlanBDocument doc, Path sharedShardsDocDir, int shardIndex);

    /**
     * Executes the operation. Called inside the cluster lock with the shard open.
     * Returns {@code true} if the shard data was modified (which triggers a push
     * back to the shared store).
     */
    boolean run(SharedFileStoreOperationContext ctx) throws IOException;
}
