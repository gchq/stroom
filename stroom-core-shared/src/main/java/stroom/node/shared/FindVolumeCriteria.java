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

package stroom.node.shared;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.node.shared.Volume.VolumeType;
import stroom.node.shared.Volume.VolumeUseStatus;

public class FindVolumeCriteria extends BaseCriteria {
    private static final long serialVersionUID = 3581257401217841946L;

    private EntityIdSet<Node> nodeIdSet = new EntityIdSet<>();
    private CriteriaSet<VolumeType> volumeTypeSet = new CriteriaSet<>();
    private CriteriaSet<VolumeUseStatus> streamStatusSet = new CriteriaSet<>();
    private CriteriaSet<VolumeUseStatus> indexStatusSet = new CriteriaSet<>();

    public EntityIdSet<Node> getNodeIdSet() {
        return nodeIdSet;
    }

    public CriteriaSet<VolumeType> getVolumeTypeSet() {
        return volumeTypeSet;
    }

    public CriteriaSet<VolumeUseStatus> getStreamStatusSet() {
        return streamStatusSet;
    }

    public CriteriaSet<VolumeUseStatus> getIndexStatusSet() {
        return indexStatusSet;
    }
}
