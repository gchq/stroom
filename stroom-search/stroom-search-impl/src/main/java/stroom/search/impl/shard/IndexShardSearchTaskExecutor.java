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

package stroom.search.impl.shard;

import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskExecutor;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class IndexShardSearchTaskExecutor extends TaskExecutor {
    static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Index Shard",
            5,
            0,
            Integer.MAX_VALUE);

    @Inject
    IndexShardSearchTaskExecutor(final ExecutorProvider executorProvider) {
        super("Stroom Search Index Shard Task Executor", executorProvider, THREAD_POOL);
    }
}
