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

package stroom.index.shared;

import stroom.task.shared.Action;
import stroom.node.shared.VolumeEntity;
import stroom.docref.DocRef;
import stroom.util.shared.VoidResult;

import java.util.Set;

public class SaveIndexVolumesAction extends Action<VoidResult> {
    private static final long serialVersionUID = -6668626615097471925L;

    private DocRef indexRef;
    private Set<VolumeEntity> volumes;

    public SaveIndexVolumesAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public SaveIndexVolumesAction(final DocRef indexRef,
                                  final Set<VolumeEntity> volumes) {
        this.indexRef = indexRef;
        this.volumes = volumes;
    }

    public DocRef getIndexRef() {
        return indexRef;
    }

    public Set<VolumeEntity> getVolumes() {
        return volumes;
    }

    @Override
    public String getTaskName() {
        return "SaveIndexVolumesAction";
    }
}
