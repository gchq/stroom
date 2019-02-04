/*
 * Copyright 2016 Crown Copyright
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

package stroom.volume;

import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class FlushVolumeClusterHandler extends AbstractTaskHandler<FlushVolumeClusterTask, VoidResult> {
    private final VolumeService volumeService;
    private final Security security;

    @Inject
    FlushVolumeClusterHandler(final VolumeService volumeService,
                              final Security security) {
        this.volumeService = volumeService;
        this.security = security;
    }

    @Override
    public VoidResult exec(final FlushVolumeClusterTask task) {
        return security.secureResult(() -> {
            volumeService.flush();
            return new VoidResult();
        });
    }
}
