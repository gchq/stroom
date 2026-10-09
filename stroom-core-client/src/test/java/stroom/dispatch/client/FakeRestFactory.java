/*
 * Copyright 2026 Crown Copyright
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
import org.fusesource.restygwt.client.Method;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// A [RestFactory] for tests that sends nothing. It keeps each request that is sent, so that a
/// test can check what the request calls and answer it, in any order.
public class FakeRestFactory implements RestFactory {

    private final List<FakeRequest<?, ?>> requests = new ArrayList<>();

    /// @return The requests sent so far, oldest first.
    public List<FakeRequest<?, ?>> getRequests() {
        return requests;
    }

    /// @return The last request sent.
    public FakeRequest<?, ?> getLastRequest() {
        return requests.getLast();
    }

    @Override
    public <T extends DirectRestService> Resource<T> create(final T service) {
        return new FakeResource<>();
    }


    // --------------------------------------------------------------------------------


    private final class FakeResource<T extends DirectRestService> implements Resource<T> {

        @Override
        public <R> MethodExecutor<T, R> method(final Function<T, R> function) {
            return new FakeRequest<>(function);
        }

        @Override
        public MethodExecutor<T, Void> call(final Consumer<T> consumer) {
            return new FakeRequest<>(resource -> {
                consumer.accept(resource);
                return null;
            });
        }
    }


    // --------------------------------------------------------------------------------


    /// A request that was sent, to check and answer.
    ///
    /// @param <T> The REST resource the request calls.
    /// @param <R> The type of its reply.
    public final class FakeRequest<T extends DirectRestService, R>
            implements MethodExecutor<T, R>, TaskExecutor<T, R> {

        private final Function<T, R> function;
        private Consumer<R> resultConsumer;
        private RestErrorHandler errorHandler;

        private FakeRequest(final Function<T, R> function) {
            this.function = function;
        }

        @Override
        public TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
            return this;
        }

        @Override
        public TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory,
                                                     final String taskMessage) {
            return this;
        }

        @Override
        public MethodExecutor<T, R> onSuccess(final Consumer<R> resultConsumer) {
            this.resultConsumer = resultConsumer;
            return this;
        }

        @Override
        public MethodExecutor<T, R> onFailure(final RestErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public void exec() {
            requests.add(this);
        }

        /// Calls the request's method on a resource, e.g. a mock, so a test can check what the
        /// request calls with which arguments.
        ///
        /// @param resource The resource to call it on.
        @SuppressWarnings("unchecked")
        public void callOn(final DirectRestService resource) {
            function.apply((T) resource);
        }

        /// Answers the request as the server would on success.
        ///
        /// @param result The reply, which may be null.
        @SuppressWarnings("unchecked")
        public void succeed(final Object result) {
            resultConsumer.accept((R) result);
        }

        /// Answers the request as a failure, e.g. no reply or an error from the server.
        ///
        /// @param throwable What went wrong.
        public void fail(final Throwable throwable) {
            errorHandler.onError(new RestError(Mockito.mock(Method.class), throwable));
        }
    }
}
