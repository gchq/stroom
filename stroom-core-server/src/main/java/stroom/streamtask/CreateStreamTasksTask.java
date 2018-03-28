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

package stroom.streamtask;

import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.ThreadPool;
import stroom.util.shared.VoidResult;
import stroom.task.ServerTask;

public class CreateStreamTasksTask extends ServerTask<VoidResult> {
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(3);

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }
}
