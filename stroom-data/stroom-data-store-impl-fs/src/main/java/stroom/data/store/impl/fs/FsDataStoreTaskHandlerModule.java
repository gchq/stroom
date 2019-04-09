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

package stroom.data.store.impl.fs;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.data.store.impl.fs.shared.DeleteFsVolumeAction;
import stroom.data.store.impl.fs.shared.FetchFsVolumeAction;
import stroom.data.store.impl.fs.shared.FindFsVolumeAction;
import stroom.data.store.impl.fs.shared.FlushFsVolumeStatusAction;
import stroom.data.store.impl.fs.shared.UpdateFsVolumeAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEvent.Handler;
import stroom.task.api.TaskHandlerBinder;

public class FsDataStoreTaskHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        TaskHandlerBinder.create(binder())
                .bind(FsCleanSubTask.class, FsCleanSubTaskHandler.class)
                .bind(DeleteFsVolumeAction.class, DeleteFSVolumeHandler.class)
                .bind(FetchFsVolumeAction.class, FetchFSVolumeHandler.class)
                .bind(FindFsVolumeAction.class, FindFsVolumeHandler.class)
                .bind(FlushFsVolumeStatusAction.class, FlushFsVolumeStatusHandler.class)
                .bind(UpdateFsVolumeAction.class, UpdateFsVolumeHandler.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(FsVolumeService.class);
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