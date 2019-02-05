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

package stroom.persist;

import stroom.lifecycle.api.AbstractLifecycleModule;
import stroom.lifecycle.api.RunnableWrapper;

import javax.inject.Inject;

public class EntityManagerLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();
        bindStartup().priority(1000).to(PersistLifecycleStartup.class);
        bindShutdown().priority(1000).to(PersistLifecycleShutdown.class);
    }

    private static class PersistLifecycleStartup extends RunnableWrapper {
        @Inject
        PersistLifecycleStartup(final PersistLifecycle persistLifecycle) {
            super(persistLifecycle::startPersistence);
        }
    }

    private static class PersistLifecycleShutdown extends RunnableWrapper {
        @Inject
        PersistLifecycleShutdown(final PersistLifecycle persistLifecycle) {
            super(persistLifecycle::stopPersistence);
        }
    }
}