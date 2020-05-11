/*
 * Copyright 2018 Crown Copyright
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

package stroom.statistics.impl.sql;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class SQLStatisticsLifecycleModule extends AbstractModule {
    @Override
    protected void configure() {

        // We need it to shutdown quite late so anything that is generating stats has had
        // a chance to finish generating
        LifecycleBinder.create(binder())
                .bindShutdownTaskTo(SQLStatisticShutdown.class, 100_000);
    }

    private static class SQLStatisticShutdown extends RunnableWrapper {
        @Inject
        SQLStatisticShutdown(final Statistics statistics) {
            super(statistics::flushAllEvents);
        }
    }
}