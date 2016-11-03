/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.server.shard;

import javax.inject.Inject;

import stroom.node.server.StroomPropertyService;
import org.springframework.stereotype.Component;

@Component
public class IndexShardSearchTaskProperties {
    private static final int DEFAULT_MAX_THREADS = 4;
    private static final int DEFAULT_MAX_THREADS_PER_TASK = 2;
    private static final int DEFAULT_MAX_OPEN_SHARDS = 5;

    private final StroomPropertyService propertyService;

    @Inject
    public IndexShardSearchTaskProperties(final StroomPropertyService propertyService) {
        this.propertyService = propertyService;
    }

    public int getMaxThreads() {
        return propertyService.getIntProperty("stroom.search.shard.maxThreads", DEFAULT_MAX_THREADS);
    }

    public int getMaxThreadsPerTask() {
        return propertyService.getIntProperty("stroom.search.shard.maxThreadsPerTask", DEFAULT_MAX_THREADS_PER_TASK);
    }

    public int getMaxOpenShards() {
        int maxOpenShards = propertyService.getIntProperty("stroom.search.shard.maxOpen", DEFAULT_MAX_OPEN_SHARDS);
        final int maxThreadsPerTask = getMaxThreadsPerTask();

        // Ensure we have at least as many cached shards as we have concurrent
        // search tasks or else some shards
        // will close as they are removed from the pool while we are still
        // searching them.
        if (maxOpenShards < maxThreadsPerTask) {
            maxOpenShards = maxThreadsPerTask;
        }

        return maxOpenShards;
    }
}
