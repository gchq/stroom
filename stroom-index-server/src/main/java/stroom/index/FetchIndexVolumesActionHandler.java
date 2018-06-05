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

package stroom.index;

import stroom.index.shared.FetchIndexVolumesAction;
import stroom.node.shared.VolumeEntity;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.SharedList;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchIndexVolumesAction.class)
class FetchIndexVolumesActionHandler extends AbstractTaskHandler<FetchIndexVolumesAction, SharedList<VolumeEntity>> {
    private final Security security;
    private final IndexVolumeService indexVolumeService;

    @Inject
    FetchIndexVolumesActionHandler(final Security security,
                                   final IndexVolumeService indexVolumeService) {
        this.security = security;
        this.indexVolumeService = indexVolumeService;
    }

    @Override
    public SharedList<VolumeEntity> exec(final FetchIndexVolumesAction action) {
        return security.secureResult(() -> new SharedList<>(indexVolumeService.getVolumesForIndex(action.getIndexRef())));
    }
}
