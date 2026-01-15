/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dispatch.client;

import stroom.task.client.Task;

import org.fusesource.restygwt.client.DirectRestService;

import java.util.function.Function;

public class RestFactoryTask<T extends DirectRestService, R> implements Task {
    private final T service;
    private final Function<T, R> function;
    private final String taskMessage;

    public RestFactoryTask(final T service, final Function<T, R> function, final String taskMessage) {
        this.service = service;
        this.function = function;
        this.taskMessage = taskMessage;
    }

    @Override
    public String toString() {
        return taskMessage;
    }
}
