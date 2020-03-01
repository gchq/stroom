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

import org.springframework.stereotype.Component;
import stroom.util.concurrent.ExecutorProvider;
import stroom.util.concurrent.ThreadPoolImpl;
import stroom.util.shared.ThreadPool;
import stroom.util.task.taskqueue.TaskExecutor;

import javax.inject.Singleton;

@Component
@Singleton
class IndexShardSearchTaskExecutor extends TaskExecutor {
    static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Index Shard",
            5,
            0,
            Integer.MAX_VALUE);

    IndexShardSearchTaskExecutor(final ExecutorProvider executorProvider) {
        super("Stroom Search Index Shard Task Executor",  executorProvider, THREAD_POOL);
    }
}
