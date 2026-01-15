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

package stroom.job.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;
import java.util.function.Consumer;

public class ScheduledJobsBinder {

    private final MapBinder<ScheduledJob, Runnable> mapBinder;

    private ScheduledJobsBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ScheduledJob.class, Runnable.class);
    }

    public static ScheduledJobsBinder create(final Binder binder) {
        return new ScheduledJobsBinder(binder);
    }

    public ScheduledJobsBinder bindJobTo(final Class<? extends Runnable> jobRunnableClass,
                                         final Consumer<ScheduledJob.Builder> jobScheduleBuilder) {
        Objects.requireNonNull(jobRunnableClass);
        Objects.requireNonNull(jobScheduleBuilder);

        final ScheduledJob.Builder builder = ScheduledJob.builder();
        jobScheduleBuilder.accept(builder);
        final ScheduledJob scheduledJob = builder.build();

        mapBinder.addBinding(scheduledJob)
                .to(jobRunnableClass);
        return this;
    }

    public MapBinder<ScheduledJob, Runnable> build() {
        return mapBinder;
    }
}
