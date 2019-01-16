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

package stroom.event.logging.impl;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.activity.shared.SetCurrentActivityAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.EventInfoProvider;
import stroom.event.logging.api.HttpServletRequestHolder;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.task.api.TaskHandlerBinder;

public class EventLoggingModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CurrentActivity.class).to(CurrentActivityImpl.class);
        bind(HttpServletRequestHolder.class).to(HttpServletRequestHolderImpl.class);
        bind(StroomEventLoggingService.class).to(StroomEventLoggingServiceImpl.class);
        bind(DocumentEventLog.class).to(DocumentEventLogImpl.class);

        Multibinder.newSetBinder(binder(), EventInfoProvider.class);

        TaskHandlerBinder.create(binder())
                .bind(SetCurrentActivityAction.class, SetCurrentActivityHandler.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}