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
import java.util.Map;
import java.util.Set;

/**
 * Implemented by any document store that manages PlanB-style shards on the
 * shared filesystem. Contributes its live (sharedPath → UUID set) data to
 * {@link SharedFileStoreCleaner} so that shard housekeeping covers
 * all registered document types, not just {@code PlanBDoc}.
 */
public interface SharedFileStoreDocStore {

    /**
     * Returns a map of shared filesystem root path to the set of document UUIDs
     * that are live (i.e. have a document config and a non-empty shared path).
     * Only entries with a non-null, non-blank shared path should be included.
     */
    Map<Path, Set<String>> getLiveSharedPathData();
}
