/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.processor.impl;

import java.util.List;

class StreamTasks {

    private final List<StreamTask> streamTasks;
    private final long totalStreamTasks;

    public StreamTasks(final List<StreamTask> streamTasks, final long totalStreamTasks) {
        this.streamTasks = streamTasks;
        this.totalStreamTasks = totalStreamTasks;
    }

    public long getTotalStreamTasks() {
        return totalStreamTasks;
    }

    public List<StreamTask> getStreamTasks() {
        return streamTasks;
    }
}
