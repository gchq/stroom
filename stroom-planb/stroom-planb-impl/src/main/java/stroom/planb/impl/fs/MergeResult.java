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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Outcome of merging one cycle's batches into a shard. {@code mergedBatchDirs} lists only the
 * batches safe to mark as merged; {@code failures} carries the rest with the error that stopped
 * each one, so the processor can count the attempt against the batch and retry it later.
 */
public record MergeResult(List<Path> mergedBatchDirs, Map<Path, Exception> failures) {

    public static MergeResult none() {
        return new MergeResult(List.of(), Map.of());
    }

    public Exception firstFailure() {
        return failures.values().stream().findFirst().orElse(null);
    }
}
