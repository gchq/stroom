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

package stroom.search.server.extraction;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.search.server.taskqueue.TaskExecutor;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.shared.ThreadPool;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@Component
@Scope(StroomScope.PROTOTYPE)
public class ExtractionTaskExecutor extends TaskExecutor {
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Stroom Data Extraction", 5, 0, Integer.MAX_VALUE);

    @Inject
    public ExtractionTaskExecutor(final ExecutorProvider executorProvider) {
        super(executorProvider, THREAD_POOL);
    }
}
