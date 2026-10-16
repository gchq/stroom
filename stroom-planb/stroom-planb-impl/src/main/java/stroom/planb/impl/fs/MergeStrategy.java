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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * How one store type gets a shard's merged batches to where its queries read them.
 *
 * <p>Called inside the per-shard cluster lock, once the processor has decided there is work to do.
 * Everything the store type does with the shard lives here — opening it, merging into it, and
 * publishing it — because store types differ in whether they keep a holding shard at all.
 *
 * <p>Bound per {@link stroom.planb.shared.StateType}. A store type with no strategy is not merged.
 */
public interface MergeStrategy {

    /**
     * Merges {@code batchDirs} for one shard and publishes the result.
     *
     * @return which batches are safe to mark merged, and how many failed. Batches left out are
     *         retried on a later cycle.
     */
    MergeResult mergeShard(MergeContext ctx, List<Path> batchDirs) throws IOException;
}
