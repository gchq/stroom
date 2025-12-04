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

import stroom.task.client.TaskMonitorFactory;

import org.fusesource.restygwt.client.DirectRestService;

import java.util.function.Consumer;
import java.util.function.Function;

public interface RestFactory {

    <T extends DirectRestService> Resource<T> create(T service);

    interface Resource<T extends DirectRestService> {

        <R> MethodExecutor<T, R> method(Function<T, R> function);

        MethodExecutor<T, Void> call(Consumer<T> consumer);
    }

    interface MethodExecutor<T extends DirectRestService, R> {

        /**
         * Set a task listener if we want to listen to the request start and finish events.
         **/
        TaskExecutor<T, R> taskMonitorFactory(TaskMonitorFactory taskMonitorFactory);

        TaskExecutor<T, R> taskMonitorFactory(TaskMonitorFactory taskMonitorFactory, String taskMessage);

        MethodExecutor<T, R> onSuccess(Consumer<R> resultConsumer);

        MethodExecutor<T, R> onFailure(RestErrorHandler errorHandler);
    }

    interface TaskExecutor<T extends DirectRestService, R> {

        void exec();
    }
}
