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

package stroom.task.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SimpleTaskContextFactory implements TaskContextFactory {

    @Override
    public Runnable context(final String taskName,
                            final Consumer<TaskContext> consumer) {
        return () -> consumer.accept(new SimpleTaskContext());
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final Consumer<TaskContext> consumer) {
        Objects.requireNonNull(parentContext, "Expecting a parent context when creating a child context");
        return () -> consumer.accept(new SimpleTaskContext());
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName,
                                         final Function<TaskContext, R> function) {
        return () -> function.apply(new SimpleTaskContext());
    }

    @Override
    public <R> Supplier<R> childContextResult(final TaskContext parentContext,
                                              final String taskName,
                                              final Function<TaskContext, R> function) {
        Objects.requireNonNull(parentContext, "Expecting a parent context when creating a child context");
        return () -> function.apply(new SimpleTaskContext());
    }


    @Override
    public Runnable context(final String taskName,
                            final TerminateHandlerFactory terminateHandlerFactory,
                            final Consumer<TaskContext> consumer) {
        return () -> consumer.accept(new SimpleTaskContext());
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final TerminateHandlerFactory terminateHandlerFactory,
                                 final Consumer<TaskContext> consumer) {
        Objects.requireNonNull(parentContext, "Expecting a parent context when creating a child context");
        return () -> consumer.accept(new SimpleTaskContext());
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName,
                                         final TerminateHandlerFactory terminateHandlerFactory,
                                         final Function<TaskContext, R> function) {
        return () -> function.apply(new SimpleTaskContext());
    }

    @Override
    public <R> Supplier<R> childContextResult(final TaskContext parentContext,
                                              final String taskName,
                                              final TerminateHandlerFactory terminateHandlerFactory,
                                              final Function<TaskContext, R> function) {
        Objects.requireNonNull(parentContext, "Expecting a parent context when creating a child context");
        return () -> function.apply(new SimpleTaskContext());
    }

    @Override
    public TaskContext current() {
        return new SimpleTaskContext();
    }
}
