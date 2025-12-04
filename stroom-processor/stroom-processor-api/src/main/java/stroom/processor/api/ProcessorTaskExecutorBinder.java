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

package stroom.processor.api;

import stroom.processor.shared.ProcessorType;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ProcessorTaskExecutorBinder {

    private final MapBinder<ProcessorType, ProcessorTaskExecutor> mapBinder;

    private ProcessorTaskExecutorBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ProcessorType.class, ProcessorTaskExecutor.class);
    }

    public static ProcessorTaskExecutorBinder create(final Binder binder) {
        return new ProcessorTaskExecutorBinder(binder);
    }

    public <H extends ProcessorTaskExecutor> ProcessorTaskExecutorBinder bind(final ProcessorType processorType,
                                                                              final Class<H> handler) {
        mapBinder.addBinding(processorType).to(handler);
        return this;
    }
}
