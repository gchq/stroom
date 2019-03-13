/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FlushFsVolumeStatusAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

class FlushFsVolumeStatusHandler extends AbstractTaskHandler<FlushFsVolumeStatusAction, VoidResult> {
    private final FsVolumeService volumeService;

    @Inject
    FlushFsVolumeStatusHandler(final FsVolumeService volumeService) {
        this.volumeService = volumeService;
    }

    @Override
    public VoidResult exec(final FlushFsVolumeStatusAction action) {
        volumeService.flush();
        return new VoidResult();
    }
}
