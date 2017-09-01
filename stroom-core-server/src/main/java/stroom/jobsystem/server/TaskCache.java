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

package stroom.jobsystem.server;

import net.sf.ehcache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.util.shared.Task;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Component
public class TaskCache extends AbstractCacheBean<String, Queue<Task<?>>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskCache.class);

    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final Map<String, TaskDestructionHandler> destructionHandlers = new HashMap<>();

    // Use the replica map to prevent us touching the pool item. If we touch
    // the pool item then no items will be evicted while the UI is showing
    // item counts.
    private final Map<String, Queue<Task<?>>> taskMap = new ConcurrentHashMap<>();

    @Inject
    public TaskCache(final CacheManager cacheManager) {
        super(cacheManager, "Task Pool", MAX_CACHE_ENTRIES);
        setMaxIdleTime(1, TimeUnit.MINUTES);
    }

    @StroomFrequencySchedule("1m")
    public void evict() {
        // Release cached tasks after 1 minute of inactivity.
        evictExpiredElements();
    }

    public Queue<Task<?>> getOrCreate(final String key) {
        return computeIfAbsent(key, this::create);
    }

    private Queue<Task<?>> create(final String job) {
        final Queue<Task<?>> queue = new ConcurrentLinkedQueue<>();
        taskMap.put(job, queue);
        return queue;
    }

    @Override
    protected void destroy(final String job, final Queue<Task<?>> queue) {
        if (job != null && queue != null) {
            final TaskDestructionHandler handler = destructionHandlers.get(job);
            if (handler != null) {
                handler.onDestroy(queue);
            }
        }
        super.destroy(job, queue);
    }

    public Integer taskCount(final String job) {
        // Use the replica map to prevent us touching the pool item. If we touch
        // the pool item then no items will be evicted while the UI is showing
        // item counts.
        final Queue<Task<?>> tasks = taskMap.get(job);
        if (tasks != null) {
            return tasks.size();
        }

        return null;
    }

    public void addDestructionHandler(final String job, final TaskDestructionHandler handler) {
        destructionHandlers.put(job, handler);
    }
}
